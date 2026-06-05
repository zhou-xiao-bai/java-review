package com.javareview.today;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.settings.ReviewedPointSchedulingPolicy;
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.TodayDtos.CreateManualTaskRequest;
import com.javareview.today.TodayDtos.RemoveReviewTasksRequest;
import com.javareview.today.TodayDtos.ReviewTaskResponse;
import com.javareview.today.TodayDtos.SummaryMetricResponse;
import com.javareview.today.TodayDtos.TaskGroupResponse;
import com.javareview.today.TodayDtos.TodayPlanResponse;
import com.javareview.today.TodayDtos.TodaySummaryResponse;

@Service
public class TodayPlanService {

	private static final int DEFAULT_TASK_MINUTES = 10;
	private static final int DUE_RESERVE_PERCENT = 40;
	private static final BigDecimal MANUAL_PRIORITY = BigDecimal.valueOf(30).setScale(2);
	private static final List<ReviewTaskStatus> UNFINISHED_STATUSES = List.of(
			ReviewTaskStatus.PENDING,
			ReviewTaskStatus.IN_PROGRESS);

	private final ReviewTaskRepository reviewTaskRepository;
	private final ReviewPointRepository reviewPointRepository;
	private final ReviewPriorityService reviewPriorityService;
	private final SettingsService settingsService;
	private final Clock clock;

	public TodayPlanService(
			ReviewTaskRepository reviewTaskRepository,
			ReviewPointRepository reviewPointRepository,
			ReviewPriorityService reviewPriorityService,
			SettingsService settingsService,
			Clock clock) {
		this.reviewTaskRepository = reviewTaskRepository;
		this.reviewPointRepository = reviewPointRepository;
		this.reviewPriorityService = reviewPriorityService;
		this.settingsService = settingsService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TodayPlanResponse getToday(User user) {
		return getPlan(user, today());
	}

	@Transactional(readOnly = true)
	public TodayPlanResponse getPlan(User user, LocalDate planDate) {
		PlanOptions options = planOptions(user);
		return toPlanResponse(
				reviewTaskRepository.findPlan(user.getId(), planDate),
				options.capacityMinutes(),
				planDate);
	}

	@Transactional
	public TodayPlanResponse generateToday(User user) {
		LocalDate today = today();
		List<ReviewTask> tasks = new ArrayList<>(reviewTaskRepository.findPlan(user.getId(), today));
		return fillPlan(user, today, tasks, planOptions(user));
	}

	@Transactional
	public TodayPlanResponse regenerateToday(User user) {
		LocalDate today = today();
		reviewTaskRepository.deletePendingGeneratedTasks(user.getId(), today);
		List<ReviewTask> tasks = new ArrayList<>(reviewTaskRepository.findPlan(user.getId(), today));
		return fillPlan(user, today, tasks, planOptions(user));
	}

	private TodayPlanResponse fillPlan(User user, LocalDate today, List<ReviewTask> tasks, PlanOptions options) {
		List<ReviewTask> newTasks = new ArrayList<>();
		Set<UUID> plannedReviewPointIds = reviewPointIds(tasks);
		int remainingMinutes = remainingCapacity(tasks, options.capacityMinutes());

		if (remainingMinutes > 0) {
			ReviewedPointSchedulingPolicy schedulingPolicy = options.reviewedPointSchedulingPolicy();
			List<ReviewTask> carryOverCandidates = carryOverCandidates(user, today, schedulingPolicy);
			List<PrioritizedPoint> dueCandidates = dueCandidates(user, today, plannedReviewPointIds, schedulingPolicy);
			int carryOverBudget = dueCandidates.isEmpty()
					? remainingMinutes
					: Math.max(0, remainingMinutes - dueReserveMinutes(remainingMinutes));
			int carryOverBudgetLeft = addCarryOverTasks(
					user,
					today,
					carryOverCandidates,
					carryOverBudget,
					plannedReviewPointIds,
					newTasks);
			remainingMinutes -= carryOverBudget - carryOverBudgetLeft;
			if (remainingMinutes > 0) {
				remainingMinutes = addDueTasks(
						user,
						today,
						dueCandidates,
						remainingMinutes,
						plannedReviewPointIds,
						newTasks);
			}
			if (remainingMinutes > 0 && !dueCandidates.isEmpty()) {
				remainingMinutes = addCarryOverTasks(
						user,
						today,
						carryOverCandidates,
						remainingMinutes,
						plannedReviewPointIds,
						newTasks);
			}
			if (remainingMinutes > 0) {
				addNewExpansionTasks(user, today, remainingMinutes, plannedReviewPointIds, newTasks);
			}
		}

		if (!newTasks.isEmpty()) {
			tasks.addAll(reviewTaskRepository.saveAll(newTasks));
		}
		return toPlanResponse(tasks, options.capacityMinutes(), today);
	}

	@Transactional
	public ReviewTaskResponse createManualTask(User user, CreateManualTaskRequest request) {
		String prompt = trimRequired(request.prompt(), "prompt");
		int estimatedMinutes = request.estimatedMinutes() == null
				? DEFAULT_TASK_MINUTES
				: request.estimatedMinutes();
		ReviewTask task = reviewTaskRepository.save(new ReviewTask(
				user,
				prompt,
				today(),
				ReviewTaskType.MANUAL,
				MANUAL_PRIORITY,
				estimatedMinutes));
		return toTaskResponse(task);
	}

	@Transactional
	public ReviewTaskResponse skipTask(User user, UUID taskId) {
		ReviewTask task = requireTask(user, taskId);
		rejectRemovedTask(task);
		task.skip(Instant.now(clock));
		return toTaskResponse(task);
	}

	@Transactional
	public ReviewTaskResponse unskipTask(User user, UUID taskId) {
		ReviewTask task = requireTask(user, taskId);
		rejectRemovedTask(task);
		if (task.getStatus() != ReviewTaskStatus.SKIPPED) {
			throw new IllegalStateException("Only skipped tasks can be restored.");
		}
		task.unskip();
		return toTaskResponse(task);
	}

	@Transactional
	public TodayPlanResponse removeTask(User user, UUID taskId) {
		return removeTasks(user, new RemoveReviewTasksRequest(List.of(taskId)));
	}

	@Transactional
	public TodayPlanResponse removeTasks(User user, RemoveReviewTasksRequest request) {
		LocalDate today = today();
		Set<UUID> requestedIds = new HashSet<>(request.taskIds());
		List<ReviewTask> tasks = reviewTaskRepository.findAllByIdsAndUserIdWithPoint(requestedIds, user.getId());
		if (tasks.size() != requestedIds.size()) {
			throw new ResourceNotFoundException("Review task not found.");
		}
		Instant removedAt = Instant.now(clock);
		for (ReviewTask task : tasks) {
			if (!task.getTaskDate().equals(today)) {
				throw new IllegalArgumentException("Only today's review tasks can be removed.");
			}
			if (!task.isRemoved()) {
				task.removeFromToday(removedAt);
			}
		}
		return toPlanResponse(reviewTaskRepository.findPlan(user.getId(), today), planOptions(user).capacityMinutes(), today);
	}

	private ReviewTask requireTask(User user, UUID taskId) {
		return reviewTaskRepository.findByIdAndUserIdWithPoint(taskId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review task not found."));
	}

	private static void rejectRemovedTask(ReviewTask task) {
		if (task.isRemoved()) {
			throw new IllegalStateException("Review task has been removed from today's plan.");
		}
	}

	private List<ReviewTask> carryOverCandidates(
			User user,
			LocalDate today,
			ReviewedPointSchedulingPolicy schedulingPolicy) {
		List<ReviewTask> carryOverCandidates = schedulingPolicy == ReviewedPointSchedulingPolicy.KEEP_REVIEWED
				? reviewTaskRepository.findCarryOverCandidatesIncludingReviewedOutsideScope(
						user.getId(),
						today,
						UNFINISHED_STATUSES)
				: reviewTaskRepository.findCarryOverCandidates(
						user.getId(),
						today,
						UNFINISHED_STATUSES);
		return carryOverCandidates.stream()
				.filter(task -> task.getReviewPoint() != null)
				.filter(task -> isCarryOverEligible(task.getReviewPoint(), schedulingPolicy))
				.toList();
	}

	private int addCarryOverTasks(
			User user,
			LocalDate today,
			List<ReviewTask> carryOverCandidates,
			int remainingMinutes,
			Set<UUID> plannedReviewPointIds,
			List<ReviewTask> newTasks) {
		for (ReviewTask oldTask : carryOverCandidates) {
			if (remainingMinutes < oldTask.getEstimatedMinutes()) {
				break;
			}
			if (!plannedReviewPointIds.add(oldTask.getReviewPoint().getId())) {
				continue;
			}
			newTasks.add(new ReviewTask(
					user,
					oldTask.getReviewPoint(),
					today,
					ReviewTaskType.CARRY_OVER,
					reviewPriorityService.forCarryOver(oldTask, today),
					oldTask.getEstimatedMinutes()));
			remainingMinutes -= oldTask.getEstimatedMinutes();
		}
		return remainingMinutes;
	}

	private List<PrioritizedPoint> dueCandidates(
			User user,
			LocalDate today,
			Set<UUID> plannedReviewPointIds,
			ReviewedPointSchedulingPolicy schedulingPolicy) {
		List<ReviewPoint> candidatePoints = schedulingPolicy == ReviewedPointSchedulingPolicy.KEEP_REVIEWED
				? reviewPointRepository.findDueCandidatesIncludingReviewedOutsideScope(
						user.getId(),
						today,
						endOfToday(today))
				: reviewPointRepository.findDueCandidates(user.getId(), today, endOfToday(today));
		return candidatePoints
				.stream()
				.filter(point -> !plannedReviewPointIds.contains(point.getId()))
				.map(point -> new PrioritizedPoint(point, reviewPriorityService.forReviewPoint(point, today, false)))
				.sorted(prioritizedPointComparator())
				.toList();
	}

	private int addDueTasks(
			User user,
			LocalDate today,
			List<PrioritizedPoint> dueCandidates,
			int remainingMinutes,
			Set<UUID> plannedReviewPointIds,
			List<ReviewTask> newTasks) {
		for (PrioritizedPoint candidate : dueCandidates) {
			if (remainingMinutes < DEFAULT_TASK_MINUTES) {
				break;
			}
			if (!plannedReviewPointIds.add(candidate.point().getId())) {
				continue;
			}
			newTasks.add(new ReviewTask(
					user,
					candidate.point(),
					today,
					ReviewTaskType.DUE,
					candidate.priority(),
					DEFAULT_TASK_MINUTES));
			remainingMinutes -= DEFAULT_TASK_MINUTES;
		}
		return remainingMinutes;
	}

	private void addNewExpansionTasks(
			User user,
			LocalDate today,
			int remainingMinutes,
			Set<UUID> plannedReviewPointIds,
			List<ReviewTask> newTasks) {
		List<PrioritizedPoint> newCandidates = reviewPointRepository
				.findNewExpansionCandidates(user.getId(), today)
				.stream()
				.filter(point -> plannedReviewPointIds.add(point.getId()))
				.map(point -> new PrioritizedPoint(point, reviewPriorityService.forReviewPoint(point, today, false)))
				.toList();
		newCandidates = newCandidates.stream()
				.sorted(prioritizedPointComparator())
				.toList();
		int minutesLeft = remainingMinutes;
		for (PrioritizedPoint candidate : newCandidates) {
			if (minutesLeft < DEFAULT_TASK_MINUTES) {
				break;
			}
			newTasks.add(new ReviewTask(
					user,
					candidate.point(),
					today,
					ReviewTaskType.NEW,
					candidate.priority(),
					DEFAULT_TASK_MINUTES));
			minutesLeft -= DEFAULT_TASK_MINUTES;
		}
	}

	private TodayPlanResponse toPlanResponse(List<ReviewTask> tasks, int capacityMinutes, LocalDate planDate) {
		List<ReviewTask> orderedTasks = tasks.stream()
				.filter(task -> !task.isRemoved())
				.sorted(Comparator
						.comparing(ReviewTask::getPriorityScore).reversed()
						.thenComparing(ReviewTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		int scheduledMinutes = orderedTasks.stream()
				.filter(TodayPlanService::countsTowardPlanCapacity)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		int completedMinutes = tasks.stream()
				.filter(task -> task.getStatus() == ReviewTaskStatus.COMPLETED)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		Map<ReviewTaskType, SummaryMetricResponse> summary = summarizeByType(orderedTasks);

		return new TodayPlanResponse(
				planDate,
				capacityMinutes,
				scheduledMinutes,
				completedMinutes,
				Math.max(0, capacityMinutes - scheduledMinutes),
				new TodaySummaryResponse(
						summary.get(ReviewTaskType.CARRY_OVER),
						summary.get(ReviewTaskType.DUE),
						summary.get(ReviewTaskType.NEW),
						summary.get(ReviewTaskType.MANUAL)),
				List.of(
						group(ReviewTaskType.CARRY_OVER, orderedTasks),
						group(ReviewTaskType.DUE, orderedTasks),
						group(ReviewTaskType.NEW, orderedTasks),
						group(ReviewTaskType.MANUAL, orderedTasks)));
	}

	private static Map<ReviewTaskType, SummaryMetricResponse> summarizeByType(List<ReviewTask> tasks) {
		Map<ReviewTaskType, SummaryMetricResponse> summary = new EnumMap<>(ReviewTaskType.class);
		for (ReviewTaskType type : ReviewTaskType.values()) {
			List<ReviewTask> typedTasks = tasks.stream()
					.filter(task -> task.getType() == type)
					.toList();
			summary.put(type, new SummaryMetricResponse(
					typedTasks.size(),
					typedTasks.stream().mapToInt(ReviewTask::getEstimatedMinutes).sum()));
		}
		return summary;
	}

	private TaskGroupResponse group(ReviewTaskType type, List<ReviewTask> tasks) {
		List<ReviewTaskResponse> typedTasks = tasks.stream()
				.filter(task -> task.getType() == type)
				.map(this::toTaskResponse)
				.toList();
		return new TaskGroupResponse(
				type.apiValue(),
				type.label(),
				typedTasks.size(),
				typedTasks.stream().mapToInt(ReviewTaskResponse::estimatedMinutes).sum(),
				typedTasks);
	}

	private ReviewTaskResponse toTaskResponse(ReviewTask task) {
		ReviewPoint point = task.getReviewPoint();
		return new ReviewTaskResponse(
				task.getId(),
				point == null ? null : point.getId(),
				point == null ? null : point.getTopic().getId(),
				point == null ? null : point.getTopic().getTitle(),
				point == null ? null : point.getTopic().getDomain().getName(),
				point == null ? null : point.getTitle(),
				task.getManualPrompt(),
				task.getTaskDate(),
				task.getType().apiValue(),
				task.getType().label(),
				planReason(task),
				task.getStatus().apiValue(),
				task.getStatus().label(),
				task.getPriorityScore(),
				task.getEstimatedMinutes(),
				dueStatus(task),
				point == null ? null : point.getNextReviewAt(),
				task.getCreatedAt(),
				task.getCompletedAt(),
				task.getRemovedAt());
	}

	private String planReason(ReviewTask task) {
		return switch (task.getType()) {
			case CARRY_OVER -> "顺延未完成";
			case DUE -> overdueDays(task) > 0 ? "逾期复验" : "到期复验";
			case NEW -> "范围新拓展";
			case MANUAL -> "今日加练";
		};
	}

	private String dueStatus(ReviewTask task) {
		if (task.getType() == ReviewTaskType.CARRY_OVER) {
			return "顺延";
		}
		if (task.getType() == ReviewTaskType.NEW) {
			return "新拓展";
		}
		if (task.getType() == ReviewTaskType.MANUAL) {
			return "加练";
		}
		ReviewPoint point = task.getReviewPoint();
		if (point == null || point.getNextReviewAt() == null) {
			return "未排期";
		}
		long days = overdueDays(task);
		if (days > 0) {
			return "逾期 " + days + " 天";
		}
		return "今日到期";
	}

	private long overdueDays(ReviewTask task) {
		ReviewPoint point = task.getReviewPoint();
		if (point == null || point.getNextReviewAt() == null) {
			return 0;
		}
		LocalDate dueDate = point.getNextReviewAt().atZone(clock.getZone()).toLocalDate();
		return ChronoUnit.DAYS.between(dueDate, task.getTaskDate());
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private Instant endOfToday(LocalDate today) {
		return today.atTime(LocalTime.MAX).atZone(clock.getZone()).toInstant();
	}

	private PlanOptions planOptions(User user) {
		UserSettings settings = settingsService.findOrDefault(user);
		return new PlanOptions(settings.getDailyReviewMinutes(), settings.getReviewedPointSchedulingPolicy());
	}

	private static Set<UUID> reviewPointIds(List<ReviewTask> tasks) {
		Set<UUID> ids = new HashSet<>();
		for (ReviewTask task : tasks) {
			if (task.getReviewPoint() != null) {
				ids.add(task.getReviewPoint().getId());
			}
		}
		return ids;
	}

	private static int remainingCapacity(List<ReviewTask> tasks, int capacityMinutes) {
		int usedMinutes = tasks.stream()
				.filter(TodayPlanService::countsTowardPlanCapacity)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		return Math.max(0, capacityMinutes - usedMinutes);
	}

	private static boolean countsTowardPlanCapacity(ReviewTask task) {
		return !task.isRemoved()
				&& task.getReviewPoint() != null
				&& task.getType() != ReviewTaskType.MANUAL
				&& task.getStatus() != ReviewTaskStatus.SKIPPED;
	}

	private static int dueReserveMinutes(int remainingMinutes) {
		if (remainingMinutes < DEFAULT_TASK_MINUTES) {
			return 0;
		}
		int reserve = Math.round(remainingMinutes * DUE_RESERVE_PERCENT / 100.0f);
		return Math.min(remainingMinutes, Math.max(DEFAULT_TASK_MINUTES, reserve));
	}

	private static boolean isCarryOverEligible(
			ReviewPoint point,
			ReviewedPointSchedulingPolicy schedulingPolicy) {
		if (point.getTopic().isAutoPlannable()) {
			return true;
		}
		return schedulingPolicy == ReviewedPointSchedulingPolicy.KEEP_REVIEWED && point.getReviewCount() > 0;
	}

	private static Comparator<PrioritizedPoint> prioritizedPointComparator() {
		return (left, right) -> {
			int result = right.priority().compareTo(left.priority());
			if (result != 0) {
				return result;
			}
			result = Integer.compare(right.point().getTopic().getInterviewValue(), left.point().getTopic().getInterviewValue());
			if (result != 0) {
				return result;
			}
			result = left.point().getTopic().getTitle().compareTo(right.point().getTopic().getTitle());
			if (result != 0) {
				return result;
			}
			return left.point().getTitle().compareTo(right.point().getTitle());
		};
	}

	private static String trimRequired(String value, String field) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return value.trim();
	}

	private record PrioritizedPoint(ReviewPoint point, BigDecimal priority) {
	}

	private record PlanOptions(
			int capacityMinutes,
			ReviewedPointSchedulingPolicy reviewedPointSchedulingPolicy) {
	}
}

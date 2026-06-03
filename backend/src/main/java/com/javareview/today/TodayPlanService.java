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
import com.javareview.settings.SettingsService;
import com.javareview.today.TodayDtos.CreateManualTaskRequest;
import com.javareview.today.TodayDtos.ReviewTaskResponse;
import com.javareview.today.TodayDtos.SummaryMetricResponse;
import com.javareview.today.TodayDtos.TaskGroupResponse;
import com.javareview.today.TodayDtos.TodayPlanResponse;
import com.javareview.today.TodayDtos.TodaySummaryResponse;

@Service
public class TodayPlanService {

	private static final int DEFAULT_TASK_MINUTES = 10;
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
		return toPlanResponse(reviewTaskRepository.findPlan(user.getId(), today()), dailyCapacityMinutes(user));
	}

	@Transactional
	public TodayPlanResponse generateToday(User user) {
		LocalDate today = today();
		List<ReviewTask> tasks = new ArrayList<>(reviewTaskRepository.findPlan(user.getId(), today));
		return fillPlan(user, today, tasks, dailyCapacityMinutes(user));
	}

	@Transactional
	public TodayPlanResponse regenerateToday(User user) {
		LocalDate today = today();
		reviewTaskRepository.deletePendingGeneratedTasks(user.getId(), today);
		List<ReviewTask> tasks = new ArrayList<>(reviewTaskRepository.findPlan(user.getId(), today));
		return fillPlan(user, today, tasks, dailyCapacityMinutes(user));
	}

	private TodayPlanResponse fillPlan(User user, LocalDate today, List<ReviewTask> tasks, int capacityMinutes) {
		List<ReviewTask> newTasks = new ArrayList<>();
		Set<UUID> plannedReviewPointIds = reviewPointIds(tasks);
		Set<String> plannedManualPrompts = manualPrompts(tasks);
		int remainingMinutes = remainingCapacity(tasks, capacityMinutes);

		if (remainingMinutes > 0) {
			remainingMinutes = addCarryOverTasks(
					user,
					today,
					remainingMinutes,
					plannedReviewPointIds,
					plannedManualPrompts,
					newTasks);
		}
		if (remainingMinutes > 0) {
			remainingMinutes = addDueTasks(user, today, remainingMinutes, plannedReviewPointIds, newTasks);
		}
		if (remainingMinutes > 0) {
			addNewExpansionTasks(user, today, remainingMinutes, plannedReviewPointIds, newTasks);
		}

		if (!newTasks.isEmpty()) {
			tasks.addAll(reviewTaskRepository.saveAll(newTasks));
		}
		return toPlanResponse(tasks, capacityMinutes);
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
		task.skip(Instant.now(clock));
		return toTaskResponse(task);
	}

	@Transactional
	public ReviewTaskResponse unskipTask(User user, UUID taskId) {
		ReviewTask task = requireTask(user, taskId);
		if (task.getStatus() != ReviewTaskStatus.SKIPPED) {
			throw new IllegalStateException("Only skipped tasks can be restored.");
		}
		task.unskip();
		return toTaskResponse(task);
	}

	private ReviewTask requireTask(User user, UUID taskId) {
		return reviewTaskRepository.findByIdAndUserIdWithPoint(taskId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review task not found."));
	}

	private int addCarryOverTasks(
			User user,
			LocalDate today,
			int remainingMinutes,
			Set<UUID> plannedReviewPointIds,
			Set<String> plannedManualPrompts,
			List<ReviewTask> newTasks) {
		List<ReviewTask> carryOverCandidates = reviewTaskRepository.findCarryOverCandidates(
				user.getId(),
				today,
				UNFINISHED_STATUSES);
		for (ReviewTask oldTask : carryOverCandidates) {
			if (remainingMinutes < oldTask.getEstimatedMinutes()) {
				break;
			}
			if (oldTask.getReviewPoint() != null && !plannedReviewPointIds.add(oldTask.getReviewPoint().getId())) {
				continue;
			}
			if (oldTask.getReviewPoint() == null && !plannedManualPrompts.add(oldTask.getManualPrompt())) {
				continue;
			}
			newTasks.add(oldTask.getReviewPoint() == null
					? new ReviewTask(
							user,
							oldTask.getManualPrompt(),
							today,
							ReviewTaskType.CARRY_OVER,
							reviewPriorityService.forCarryOver(oldTask, today),
							oldTask.getEstimatedMinutes())
					: new ReviewTask(
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

	private int addDueTasks(
			User user,
			LocalDate today,
			int remainingMinutes,
			Set<UUID> plannedReviewPointIds,
			List<ReviewTask> newTasks) {
		List<PrioritizedPoint> dueCandidates = reviewPointRepository
				.findDueCandidates(user.getId(), today, endOfToday(today))
				.stream()
				.filter(point -> plannedReviewPointIds.add(point.getId()))
				.map(point -> new PrioritizedPoint(point, reviewPriorityService.forReviewPoint(point, today, false)))
				.sorted(Comparator.comparing(PrioritizedPoint::priority).reversed())
				.toList();
		for (PrioritizedPoint candidate : dueCandidates) {
			if (remainingMinutes < DEFAULT_TASK_MINUTES) {
				break;
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
				.sorted(Comparator.comparing(PrioritizedPoint::priority).reversed())
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

	private TodayPlanResponse toPlanResponse(List<ReviewTask> tasks, int capacityMinutes) {
		List<ReviewTask> orderedTasks = tasks.stream()
				.sorted(Comparator
						.comparing(ReviewTask::getPriorityScore).reversed()
						.thenComparing(ReviewTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		int scheduledMinutes = orderedTasks.stream()
				.filter(task -> task.getStatus() != ReviewTaskStatus.SKIPPED)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		int completedMinutes = orderedTasks.stream()
				.filter(task -> task.getStatus() == ReviewTaskStatus.COMPLETED)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		Map<ReviewTaskType, SummaryMetricResponse> summary = summarizeByType(orderedTasks);

		return new TodayPlanResponse(
				today(),
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
				task.getStatus().apiValue(),
				task.getStatus().label(),
				task.getPriorityScore(),
				task.getEstimatedMinutes(),
				dueStatus(task),
				point == null ? null : point.getNextReviewAt(),
				task.getCreatedAt(),
				task.getCompletedAt());
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
		LocalDate dueDate = point.getNextReviewAt().atZone(clock.getZone()).toLocalDate();
		long days = ChronoUnit.DAYS.between(dueDate, task.getTaskDate());
		if (days > 0) {
			return "逾期 " + days + " 天";
		}
		return "今日到期";
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private Instant endOfToday(LocalDate today) {
		return today.atTime(LocalTime.MAX).atZone(clock.getZone()).toInstant();
	}

	private int dailyCapacityMinutes(User user) {
		return settingsService.findOrDefault(user).getDailyReviewMinutes();
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

	private static Set<String> manualPrompts(List<ReviewTask> tasks) {
		Set<String> prompts = new HashSet<>();
		for (ReviewTask task : tasks) {
			if (task.getManualPrompt() != null) {
				prompts.add(task.getManualPrompt());
			}
		}
		return prompts;
	}

	private static int remainingCapacity(List<ReviewTask> tasks, int capacityMinutes) {
		int usedMinutes = tasks.stream()
				.filter(task -> task.getStatus() != ReviewTaskStatus.SKIPPED)
				.mapToInt(ReviewTask::getEstimatedMinutes)
				.sum();
		return Math.max(0, capacityMinutes - usedMinutes);
	}

	private static String trimRequired(String value, String field) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return value.trim();
	}

	private record PrioritizedPoint(ReviewPoint point, BigDecimal priority) {
	}
}

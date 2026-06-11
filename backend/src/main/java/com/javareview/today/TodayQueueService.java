package com.javareview.today;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.reviewunit.ReviewAttempt;
import com.javareview.reviewunit.ReviewAttemptRepository;
import com.javareview.reviewunit.ReviewAttemptResult;
import com.javareview.reviewunit.ReviewAttemptSource;
import com.javareview.reviewunit.TodayReviewAction;
import com.javareview.reviewunit.TodayReviewActionRepository;
import com.javareview.reviewunit.TodayReviewActionType;
import com.javareview.reviewunit.UserReviewUnitState;
import com.javareview.reviewunit.UserReviewUnitStateRepository;
import com.javareview.reviewunit.UserReviewUnitStatus;
import com.javareview.today.TodayDtos.TodayActionRequest;
import com.javareview.today.TodayDtos.TodayQueueGroupResponse;
import com.javareview.today.TodayDtos.TodayQueueItemResponse;
import com.javareview.today.TodayDtos.TodayQueueResponse;

@Service
public class TodayQueueService {

	private final UserReviewUnitStateRepository stateRepository;
	private final TodayReviewActionRepository actionRepository;
	private final ReviewAttemptRepository attemptRepository;
	private final Clock clock;

	public TodayQueueService(
			UserReviewUnitStateRepository stateRepository,
			TodayReviewActionRepository actionRepository,
			ReviewAttemptRepository attemptRepository,
			Clock clock) {
		this.stateRepository = stateRepository;
		this.actionRepository = actionRepository;
		this.attemptRepository = attemptRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TodayQueueResponse getQueue(User user) {
		LocalDate today = LocalDate.now(clock);
		List<UserReviewUnitState> states = stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE));
		List<TodayReviewAction> actions = actionRepository.findByUserIdAndActionDate(user.getId(), today);
		Set<UUID> dismissedUnitIds = actionUnitIds(actions, TodayReviewActionType.DISMISS_TODAY);
		Set<UUID> manualUnitIds = actionUnitIds(actions, TodayReviewActionType.MANUAL_ADD);
		Map<UUID, UserReviewUnitState> statesByUnitId = states.stream()
				.collect(Collectors.toMap(state -> state.getReviewUnit().getId(), Function.identity(), (left, right) -> left));

		Map<UUID, TodayQueueItemResponse> items = new LinkedHashMap<>();
		for (UserReviewUnitState state : states) {
			UUID unitId = state.getReviewUnit().getId();
			if (dismissedUnitIds.contains(unitId)) {
				continue;
			}
			QueueReason reason = reasonFor(state, manualUnitIds.contains(unitId), today);
			if (reason != null) {
				items.put(unitId, toItem(state, reason));
			}
		}
		for (UUID unitId : manualUnitIds) {
			if (dismissedUnitIds.contains(unitId)) {
				continue;
			}
			UserReviewUnitState state = statesByUnitId.get(unitId);
			if (state != null) {
				items.putIfAbsent(unitId, toItem(state, QueueReason.MANUAL_ADD));
			}
		}

		List<TodayQueueItemResponse> orderedItems = items.values()
				.stream()
				.sorted(itemComparator())
				.toList();
		return new TodayQueueResponse(today, groupItems(orderedItems));
	}

	@Transactional
	public TodayQueueResponse applyAction(User user, TodayActionRequest request) {
		UserReviewUnitState state = requireReviewUnitState(user, request.reviewUnitStateId());
		if (state.getStatus() == UserReviewUnitStatus.ARCHIVED || state.getStatus() == UserReviewUnitStatus.NOT_FOR_ME) {
			throw new IllegalStateException("Review unit is not actionable.");
		}
		LocalDate today = LocalDate.now(clock);
		TodayReviewActionType actionType = request.actionType();
		switch (actionType) {
			case DISMISS_TODAY, MANUAL_ADD -> saveAction(user, state.getReviewUnit(), today, actionType, null);
			case POSTPONE -> postpone(user, state, today, request.postponeUntil());
			case SELF_MASTERED -> selfMastered(user, state, today);
		}
		return getQueue(user);
	}

	private UserReviewUnitState requireReviewUnitState(User user, UUID stateId) {
		return stateRepository.findByIdAndUserIdWithUnit(stateId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review unit state not found."));
	}

	private void postpone(User user, UserReviewUnitState state, LocalDate today, LocalDate postponeUntil) {
		LocalDate targetDate = postponeUntil == null ? today.plusDays(1) : postponeUntil;
		if (!targetDate.isAfter(today)) {
			throw new IllegalArgumentException("postponeUntil must be after today.");
		}
		Instant nextReviewAt = startOfDay(targetDate);
		state.postpone(nextReviewAt);
		state.getReviewUnit().postpone(nextReviewAt);
		saveAction(user, state.getReviewUnit(), today, TodayReviewActionType.POSTPONE, targetDate);
	}

	private void selfMastered(User user, UserReviewUnitState state, LocalDate today) {
		Instant now = Instant.now(clock);
		Instant nextReviewAt = now.plus(java.time.Duration.ofDays(30));
		ReviewPoint unit = state.getReviewUnit();
		state.recordAttempt(ReviewAttemptResult.SELF_MASTERED, now, nextReviewAt);
		unit.updateReviewProgress(
				BigDecimal.valueOf(5).setScale(2),
				ReviewPointStatus.LONG_TERM,
				now,
				nextReviewAt,
				unit.getReviewCount() + 1,
				unit.getWrongCount(),
				List.of(),
				"用户自评已掌握，后续可用变体题低频复验。");
		attemptRepository.save(new ReviewAttempt(
				user,
				unit,
				null,
				ReviewAttemptSource.SELF_ASSESS,
				ReviewAttemptResult.SELF_MASTERED,
				BigDecimal.valueOf(5).setScale(2),
				now,
				"用户在今日队列中标记已掌握。"));
		saveAction(user, unit, today, TodayReviewActionType.SELF_MASTERED, null);
	}

	private void saveAction(
			User user,
			ReviewPoint reviewUnit,
			LocalDate actionDate,
			TodayReviewActionType actionType,
			LocalDate postponeUntil) {
		actionRepository.save(new TodayReviewAction(user, reviewUnit, actionDate, actionType, postponeUntil));
	}

	private Instant startOfDay(LocalDate date) {
		return date.atStartOfDay(clock.getZone()).toInstant();
	}

	private QueueReason reasonFor(UserReviewUnitState state, boolean manualAdded, LocalDate today) {
		if (state.getNextReviewAt() != null) {
			LocalDate dueDate = state.getNextReviewAt().atZone(clock.getZone()).toLocalDate();
			if (dueDate.isBefore(today)) {
				return QueueReason.OVERDUE;
			}
			if (dueDate.equals(today)) {
				return QueueReason.DUE_TODAY;
			}
			if (dueDate.isAfter(today)) {
				return manualAdded ? QueueReason.MANUAL_ADD : null;
			}
		}
		if (manualAdded) {
			return QueueReason.MANUAL_ADD;
		}
		if (state.getStatus() == UserReviewUnitStatus.PENDING_FIRST_REVIEW) {
			return QueueReason.PENDING_FIRST_REVIEW;
		}
		return null;
	}

	private TodayQueueItemResponse toItem(UserReviewUnitState state, QueueReason reason) {
		ReviewPoint unit = state.getReviewUnit();
		return new TodayQueueItemResponse(
				unit.getId(),
				state.getId(),
				unit.getTopic().getId(),
				unit.getTopic().getTitle(),
				unit.getTopic().getDomain().getName(),
				unit.getTitle(),
				state.getStatus().name(),
				reason.apiValue(),
				reason.label(),
				unit.getImportance(),
				unit.getDifficulty(),
				unit.getInterviewFrequency(),
				state.getNextReviewAt(),
				state.getAdmittedAt(),
				state.getLastReviewedAt(),
				state.getLastResult() == null ? null : state.getLastResult().name(),
				state.getConsecutiveSuccessCount(),
				state.getConsecutiveFailureCount());
	}

	private List<TodayQueueGroupResponse> groupItems(List<TodayQueueItemResponse> items) {
		Map<QueueReason, List<TodayQueueItemResponse>> byReason = items.stream()
				.collect(Collectors.groupingBy(
						item -> QueueReason.fromApiValue(item.reason()),
						() -> new EnumMap<>(QueueReason.class),
						Collectors.toList()));
		return List.of(QueueReason.OVERDUE, QueueReason.DUE_TODAY, QueueReason.MANUAL_ADD, QueueReason.PENDING_FIRST_REVIEW)
				.stream()
				.map(reason -> new TodayQueueGroupResponse(
						reason.apiValue(),
						reason.label(),
						byReason.getOrDefault(reason, List.of()).size(),
						byReason.getOrDefault(reason, List.of())))
				.toList();
	}

	private Comparator<TodayQueueItemResponse> itemComparator() {
		return Comparator
				.comparing((TodayQueueItemResponse item) -> QueueReason.fromApiValue(item.reason()).rank())
				.thenComparing(TodayQueueItemResponse::nextReviewAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TodayQueueItemResponse::importance, Comparator.reverseOrder())
				.thenComparing(TodayQueueItemResponse::interviewFrequency, Comparator.reverseOrder())
				.thenComparing(TodayQueueItemResponse::difficulty, Comparator.reverseOrder())
				.thenComparing(TodayQueueItemResponse::admittedAt, Comparator.nullsLast(Comparator.naturalOrder()));
	}

	private static Set<UUID> actionUnitIds(List<TodayReviewAction> actions, TodayReviewActionType type) {
		return latestActionsByUnitId(actions).values().stream()
				.filter(action -> action.getActionType() == type)
				.map(action -> action.getReviewUnit().getId())
				.collect(Collectors.toSet());
	}

	private static Map<UUID, TodayReviewAction> latestActionsByUnitId(List<TodayReviewAction> actions) {
		Map<UUID, TodayReviewAction> latest = new LinkedHashMap<>();
		for (TodayReviewAction action : actions) {
			latest.put(action.getReviewUnit().getId(), action);
		}
		return latest;
	}

	private enum QueueReason {
		OVERDUE("overdue", "逾期复习", 1),
		DUE_TODAY("due_today", "今日到期", 2),
		MANUAL_ADD("manual_add", "手动加入今日", 3),
		PENDING_FIRST_REVIEW("pending_first_review", "待首考", 4);

		private final String apiValue;
		private final String label;
		private final int rank;

		QueueReason(String apiValue, String label, int rank) {
			this.apiValue = apiValue;
			this.label = label;
			this.rank = rank;
		}

		String apiValue() {
			return apiValue;
		}

		String label() {
			return label;
		}

		int rank() {
			return rank;
		}

		static QueueReason fromApiValue(String value) {
			for (QueueReason reason : values()) {
				if (reason.apiValue.equals(value)) {
					return reason;
				}
			}
			throw new IllegalArgumentException("Unknown queue reason: " + value);
		}
	}

}

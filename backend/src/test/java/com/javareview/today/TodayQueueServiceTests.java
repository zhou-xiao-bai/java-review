package com.javareview.today;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
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
import com.javareview.topic.Domain;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class TodayQueueServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 6, 9);
	private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

	@Mock
	private UserReviewUnitStateRepository stateRepository;

	@Mock
	private TodayReviewActionRepository actionRepository;

	@Mock
	private ReviewAttemptRepository attemptRepository;

	private TodayQueueService todayQueueService;
	private User user;
	private Topic topic;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		todayQueueService = new TodayQueueService(stateRepository, actionRepository, attemptRepository, clock);
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		topic = new Topic(new Domain(java.util.UUID.randomUUID(), "mysql", "MySQL", 80),
				"mysql-indexes", "索引", TopicSource.BUILTIN, true);
	}

	@Test
	void queueGroupsOverdueDueManualAndPendingFirstReviewWithoutCreatingTasks() {
		UserReviewUnitState overdue = activeState("B+Tree 查询路径", TODAY.minusDays(1));
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		UserReviewUnitState manual = activeState("EXPLAIN 判断索引使用", TODAY.plusDays(7));
		UserReviewUnitState pendingFirst = pendingState("最左前缀和索引失效");
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(pendingFirst, manual, dueToday, overdue));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY))
				.thenReturn(List.of(new TodayReviewAction(
						user,
						manual.getReviewUnit(),
						TODAY,
						TodayReviewActionType.MANUAL_ADD,
						null)));

		var response = todayQueueService.getQueue(user);

		assertThat(response.groups()).extracting(group -> group.reason())
				.containsExactly("overdue", "due_today", "manual_add", "pending_first_review");
		assertThat(response.groups().get(0).items()).extracting(item -> item.unitTitle())
				.containsExactly("B+Tree 查询路径");
		assertThat(response.groups().get(1).items()).extracting(item -> item.unitTitle())
				.containsExactly("覆盖索引与回表");
		assertThat(response.groups().get(2).items()).extracting(item -> item.unitTitle())
				.containsExactly("EXPLAIN 判断索引使用");
		assertThat(response.groups().get(3).items()).extracting(item -> item.unitTitle())
				.containsExactly("最左前缀和索引失效");
	}

	@Test
	void dismissTodayHidesItemOnlyForQueueDate() {
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(dueToday));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY))
				.thenReturn(List.of(new TodayReviewAction(
						user,
						dueToday.getReviewUnit(),
						TODAY,
						TodayReviewActionType.DISMISS_TODAY,
						null)));

		var response = todayQueueService.getQueue(user);

		assertThat(response.groups()).allSatisfy(group -> assertThat(group.items()).isEmpty());
	}

	@Test
	void dueReasonWinsWhenItemIsAlsoManuallyAdded() {
		UserReviewUnitState overdue = activeState("B+Tree 查询路径", TODAY.minusDays(1));
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(overdue));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY))
				.thenReturn(List.of(new TodayReviewAction(
						user,
						overdue.getReviewUnit(),
						TODAY,
						TodayReviewActionType.MANUAL_ADD,
						null)));

		var response = todayQueueService.getQueue(user);

		assertThat(response.groups().get(0).items()).hasSize(1);
		assertThat(response.groups().get(0).items().getFirst().reason()).isEqualTo("overdue");
		assertThat(response.groups().get(2).items()).isEmpty();
	}

	@Test
	void latestTodayActionWinsForQueueVisibility() {
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(dueToday));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY))
				.thenReturn(List.of(
						new TodayReviewAction(
								user,
								dueToday.getReviewUnit(),
								TODAY,
								TodayReviewActionType.DISMISS_TODAY,
								null),
						new TodayReviewAction(
								user,
								dueToday.getReviewUnit(),
								TODAY,
								TodayReviewActionType.MANUAL_ADD,
								null)));

		var response = todayQueueService.getQueue(user);

		assertThat(response.groups().get(1).items()).hasSize(1);
		assertThat(response.groups().get(1).items().getFirst().reason()).isEqualTo("due_today");
	}

	@Test
	void postponeUpdatesLongTermScheduleAndRemovesItemFromTodayQueue() {
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		when(stateRepository.findByIdAndUserIdWithUnit(dueToday.getId(), user.getId()))
				.thenReturn(Optional.of(dueToday));
		when(actionRepository.save(any(TodayReviewAction.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(dueToday));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY)).thenReturn(List.of());

		var response = todayQueueService.applyAction(user, new TodayActionRequest(
				dueToday.getId(),
				TodayReviewActionType.POSTPONE,
				TODAY.plusDays(2)));

		assertThat(dueToday.getNextReviewAt()).isEqualTo(TODAY.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC));
		assertThat(dueToday.getReviewUnit().getNextReviewAt()).isEqualTo(dueToday.getNextReviewAt());
		assertThat(response.groups()).allSatisfy(group -> assertThat(group.items()).isEmpty());

		ArgumentCaptor<TodayReviewAction> actionCaptor = ArgumentCaptor.forClass(TodayReviewAction.class);
		org.mockito.Mockito.verify(actionRepository).save(actionCaptor.capture());
		assertThat(actionCaptor.getValue().getActionType()).isEqualTo(TodayReviewActionType.POSTPONE);
		assertThat(actionCaptor.getValue().getPostponeUntil()).isEqualTo(TODAY.plusDays(2));
	}

	@Test
	void postponeRejectsPastOrTodayDate() {
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		when(stateRepository.findByIdAndUserIdWithUnit(dueToday.getId(), user.getId()))
				.thenReturn(Optional.of(dueToday));

		assertThatThrownBy(() -> todayQueueService.applyAction(user, new TodayActionRequest(
				dueToday.getId(),
				TodayReviewActionType.POSTPONE,
				TODAY)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("postponeUntil must be after today");
	}

	@Test
	void selfMasteredWritesSelfAssessAttemptAndSchedulesLongTermReview() {
		UserReviewUnitState dueToday = activeState("覆盖索引与回表", TODAY);
		when(stateRepository.findByIdAndUserIdWithUnit(dueToday.getId(), user.getId()))
				.thenReturn(Optional.of(dueToday));
		when(actionRepository.save(any(TodayReviewAction.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(attemptRepository.save(any(ReviewAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(stateRepository.findQueueCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(dueToday));
		when(actionRepository.findByUserIdAndActionDate(user.getId(), TODAY)).thenReturn(List.of());

		var response = todayQueueService.applyAction(user, new TodayActionRequest(
				dueToday.getId(),
				TodayReviewActionType.SELF_MASTERED,
				null));

		assertThat(dueToday.getLastResult()).isEqualTo(ReviewAttemptResult.SELF_MASTERED);
		assertThat(dueToday.getNextReviewAt()).isEqualTo(NOW.plusSeconds(30L * 86_400));
		assertThat(dueToday.getReviewUnit().getStatus()).isEqualTo(ReviewPointStatus.LONG_TERM);
		assertThat(dueToday.getReviewUnit().getNextReviewAt()).isEqualTo(dueToday.getNextReviewAt());
		assertThat(response.groups()).allSatisfy(group -> assertThat(group.items()).isEmpty());

		ArgumentCaptor<ReviewAttempt> attemptCaptor = ArgumentCaptor.forClass(ReviewAttempt.class);
		org.mockito.Mockito.verify(attemptRepository).save(attemptCaptor.capture());
		ReviewAttempt attempt = attemptCaptor.getValue();
		assertThat(attempt).extracting("source").isEqualTo(ReviewAttemptSource.SELF_ASSESS);
		assertThat(attempt).extracting("result").isEqualTo(ReviewAttemptResult.SELF_MASTERED);
		ArgumentCaptor<TodayReviewAction> actionCaptor = ArgumentCaptor.forClass(TodayReviewAction.class);
		org.mockito.Mockito.verify(actionRepository).save(actionCaptor.capture());
		assertThat(actionCaptor.getValue().getActionType()).isEqualTo(TodayReviewActionType.SELF_MASTERED);
	}

	private UserReviewUnitState pendingState(String title) {
		return new UserReviewUnitState(user, point(title), NOW);
	}

	private UserReviewUnitState activeState(String title, LocalDate nextReviewDate) {
		UserReviewUnitState state = pendingState(title);
		state.recordAttempt(
				ReviewAttemptResult.GOOD,
				NOW.minusSeconds(3600),
				nextReviewDate.atStartOfDay().toInstant(ZoneOffset.UTC));
		return state;
	}

	private ReviewPoint point(String title) {
		return new ReviewPoint(topic, title, 5, 4, 5, "next probe");
	}
}

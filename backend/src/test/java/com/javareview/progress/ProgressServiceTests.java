package com.javareview.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.reviewpoint.ReviewWeaknessEvent;
import com.javareview.reviewpoint.ReviewWeaknessEventRepository;
import com.javareview.reviewunit.ReviewAttemptResult;
import com.javareview.reviewunit.UserReviewUnitState;
import com.javareview.reviewunit.UserReviewUnitStateRepository;
import com.javareview.reviewunit.UserReviewUnitStatus;
import com.javareview.reviewsession.ReviewSession;
import com.javareview.reviewsession.ReviewSessionRepository;
import com.javareview.reviewsession.ReviewSessionStatus;
import com.javareview.progress.ProgressDtos.DomainProgressResponse;
import com.javareview.progress.ProgressDtos.TopicProgressResponse;
import com.javareview.topic.Domain;
import com.javareview.topic.RelevanceTier;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicRepository;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTests {

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private ReviewPointRepository reviewPointRepository;

	@Mock
	private ReviewWeaknessEventRepository weaknessEventRepository;

	@Mock
	private ReviewSessionRepository reviewSessionRepository;

	@Mock
	private UserReviewUnitStateRepository reviewUnitStateRepository;

	private ProgressService progressService;
	private User user;

	@BeforeEach
	void setUp() {
		progressService = new ProgressService(
				topicRepository,
				reviewPointRepository,
				weaknessEventRepository,
				reviewSessionRepository,
				reviewUnitStateRepository,
				Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
	}

	@Test
	void overviewReturnsDefaultsWhenNoScopeExists() {
		when(topicRepository.findAllWithDomain()).thenReturn(List.of());
		when(reviewUnitStateRepository.findByUserIdAndStatusInWithUnit(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of());
		when(reviewSessionRepository.countByUserIdAndStatus(user.getId(), ReviewSessionStatus.EVALUATED)).thenReturn(0L);

		var response = progressService.overview(user);

		assertThat(response.overallMastery()).isEqualByComparingTo("0.00");
		assertThat(response.selectedTopicCount()).isZero();
		assertThat(response.reviewPointCount()).isZero();
		assertThat(response.unstablePointCount()).isZero();
		assertThat(response.dueReviewPointCount()).isZero();
		assertThat(response.completedSessionCount()).isZero();
		assertThat(response.openWeaknessCount()).isZero();
		assertThat(response.highRiskPointCount()).isZero();
		assertThat(response.autoPlannableTopicCount()).isZero();
	}

	@Test
	void listsReturnEmptyDefaultsWhenNoScopeExists() {
		when(topicRepository.findAllWithDomain()).thenReturn(List.of());
		when(reviewUnitStateRepository.findByUserIdAndStatusInWithUnit(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of());

		assertThat(progressService.domains(user)).isEmpty();
		assertThat(progressService.topics(user, null)).isEmpty();
		assertThat(progressService.weakPoints(user)).isEmpty();
		assertThat(progressService.dueReviewPoints(user)).isEmpty();
	}

	@Test
	void overviewDoesNotTreatUncoveredPointsAsHighRisk() {
		Topic coreTopic = topic("spring-transactions", "Spring 事务");
		Topic supplementTopic = topic("java-date-time", "java.time");
		supplementTopic.updatePlanning(RelevanceTier.SUPPLEMENT, false, 1);
		ReviewPoint uncoveredPoint = point(coreTopic, "事务代理生效边界");
		ReviewPoint lowReviewedPoint = point(coreTopic, "传播行为与嵌套调用");
		lowReviewedPoint.updateReviewProgress(
				BigDecimal.valueOf(2.0),
				ReviewPointStatus.FIRST_PASS,
				Instant.parse("2026-06-01T00:00:00Z"),
				Instant.parse("2026-06-05T00:00:00Z"),
				1,
				0,
				List.of(),
				"next probe");
		when(topicRepository.findAllWithDomain()).thenReturn(List.of(coreTopic, supplementTopic));
		when(reviewUnitStateRepository.findByUserIdAndStatusInWithUnit(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of());
		when(reviewPointRepository.findByTopicIdIn(List.of(coreTopic.getId(), supplementTopic.getId())))
				.thenReturn(List.of(uncoveredPoint, lowReviewedPoint));
		when(weaknessEventRepository.findByReviewPoint_IdIn(anyCollection())).thenReturn(List.of());
		when(reviewSessionRepository.countByUserIdAndStatus(user.getId(), ReviewSessionStatus.EVALUATED)).thenReturn(1L);

		var response = progressService.overview(user);

		assertThat(response.reviewPointCount()).isEqualTo(2);
		assertThat(response.highRiskPointCount()).isEqualTo(1);
		assertThat(response.autoPlannableTopicCount()).isEqualTo(1);
	}

	@Test
	void progressIncludesAdmittedTopicEvenWhenItIsNotCurrentlySelected() {
		Domain domain = new Domain(UUID.randomUUID(), "database", "数据库", 40);
		Topic selectedTopic = new Topic(domain, "spring-transactions", "Spring 事务", TopicSource.BUILTIN, true);
		Topic admittedTopic = new Topic(domain, "mysql-index", "MySQL 索引", TopicSource.BUILTIN, true);
		admittedTopic.setSelected(false);
		ReviewPoint selectedPoint = point(selectedTopic, "事务代理生效边界");
		ReviewPoint admittedPoint = point(admittedTopic, "B+Tree 查询路径");
		UserReviewUnitState admittedState = new UserReviewUnitState(user, admittedPoint, Instant.parse("2026-06-02T00:00:00Z"));
		admittedState.recordAttempt(
				ReviewAttemptResult.GOOD,
				Instant.parse("2026-06-03T00:00:00Z"),
				Instant.parse("2026-06-05T00:00:00Z"));
		when(topicRepository.findAllWithDomain()).thenReturn(List.of(selectedTopic, admittedTopic));
		when(reviewUnitStateRepository.findByUserIdAndStatusInWithUnit(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of(admittedState));
		when(reviewPointRepository.findByTopicIdIn(anyCollection()))
				.thenReturn(List.of(selectedPoint, admittedPoint));
		when(weaknessEventRepository.findByReviewPoint_IdIn(anyCollection())).thenReturn(List.of());
		when(reviewSessionRepository.countByUserIdAndStatus(user.getId(), ReviewSessionStatus.EVALUATED)).thenReturn(0L);

		var overview = progressService.overview(user);
		var topics = progressService.topics(user, null);
		var domains = progressService.domains(user);

		assertThat(overview.selectedTopicCount()).isEqualTo(1);
		assertThat(overview.reviewPointCount()).isEqualTo(2);
		assertThat(topics).extracting(TopicProgressResponse::topicTitle)
				.containsExactlyInAnyOrder("Spring 事务", "MySQL 索引");
		assertThat(domains).extracting(DomainProgressResponse::reviewPointCount).containsExactly(2L);
	}

	@Test
	void topicsExposeUncoveredStatusAndOpenWeaknessSummary() {
		Topic topic = topic("spring-transactions", "Spring 事务");
		ReviewPoint point = point(topic, "事务代理生效边界");
		ReviewWeaknessEvent event = weaknessEvent(point, "missing_boundary", "缺少事务失效边界");
		when(topicRepository.findAllWithDomain()).thenReturn(List.of(topic));
		when(reviewUnitStateRepository.findByUserIdAndStatusInWithUnit(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE)))
				.thenReturn(List.of());
		when(reviewPointRepository.findByTopicIdIn(List.of(topic.getId()))).thenReturn(List.of(point));
		when(weaknessEventRepository.findByReviewPoint_IdIn(List.of(point.getId()))).thenReturn(List.of(event));

		var response = progressService.topics(user, null);

		assertThat(response).hasSize(1);
		assertThat(response.getFirst().status()).isEqualTo("uncovered");
		assertThat(response.getFirst().openWeaknessCount()).isEqualTo(1);
		assertThat(response.getFirst().weakPointSummary()).containsExactly("缺少事务失效边界");
		verify(weaknessEventRepository).findByReviewPoint_IdIn(List.of(point.getId()));
	}

	@Test
	void reviewPlanCalendarUsesReviewUnitStatesAsPlanSource() {
		Topic topic = topic("spring-transactions", "Spring 事务");
		ReviewPoint generatedPoint = duePoint(topic, "事务代理生效边界", LocalDate.of(2026, 6, 3));
		ReviewPoint overduePoint = duePoint(topic, "传播行为与嵌套调用", LocalDate.of(2026, 6, 1));
		ReviewPoint futurePoint = duePoint(topic, "事务上下文与线程绑定", LocalDate.of(2026, 6, 4));
		ReviewPoint pendingPoint = point(topic, "事务异常回滚规则");
		UserReviewUnitState generatedState = activeState(generatedPoint, LocalDate.of(2026, 6, 3));
		UserReviewUnitState overdueState = activeState(overduePoint, LocalDate.of(2026, 6, 1));
		UserReviewUnitState futureState = activeState(futurePoint, LocalDate.of(2026, 6, 4));
		UserReviewUnitState pendingState = new UserReviewUnitState(user, pendingPoint, Instant.parse("2026-06-02T00:00:00Z"));
		when(reviewUnitStateRepository.findCalendarCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE),
				Instant.parse("2026-06-05T23:59:59.999999999Z")))
				.thenReturn(List.of(generatedState, overdueState, futureState, pendingState));

		var response = progressService.reviewPlanCalendar(user, LocalDate.of(2026, 6, 3), 3);

		assertThat(response.days()).hasSize(3);
		assertThat(response.days().get(0).items())
				.extracting(item -> item.pointTitle())
				.containsExactly("传播行为与嵌套调用", "事务代理生效边界", "事务异常回滚规则");
		assertThat(response.days().get(0).items())
				.extracting(item -> item.source())
				.containsOnly("review_unit_state");
		assertThat(response.days().get(0).items().get(0).type()).isEqualTo("due");
		assertThat(response.days().get(0).items().get(0).dueStatus()).isEqualTo("逾期 2 天");
		assertThat(response.days().get(0).items().get(2).type()).isEqualTo("pending_first_review");
		assertThat(response.days().get(0).items().get(2).dueStatus()).isEqualTo("待首考");
		assertThat(response.days().get(1).items())
				.extracting(item -> item.pointTitle())
				.containsExactly("事务上下文与线程绑定");
		assertThat(response.days().get(2).items()).isEmpty();
	}

	@Test
	void reviewPlanCalendarPlacesPostponedFirstReviewOnItsFutureDate() {
		Topic topic = topic("spring-transactions", "Spring 事务");
		ReviewPoint pendingPoint = point(topic, "事务异常回滚规则");
		UserReviewUnitState pendingState = new UserReviewUnitState(user, pendingPoint, Instant.parse("2026-06-02T00:00:00Z"));
		pendingState.postpone(Instant.parse("2026-06-05T00:00:00Z"));
		when(reviewUnitStateRepository.findCalendarCandidates(
				user.getId(),
				List.of(UserReviewUnitStatus.PENDING_FIRST_REVIEW, UserReviewUnitStatus.ACTIVE),
				Instant.parse("2026-06-05T23:59:59.999999999Z")))
				.thenReturn(List.of(pendingState));

		var response = progressService.reviewPlanCalendar(user, LocalDate.of(2026, 6, 3), 3);

		assertThat(response.days().get(0).items()).isEmpty();
		assertThat(response.days().get(1).items()).isEmpty();
		assertThat(response.days().get(2).items())
				.extracting(item -> item.pointTitle())
				.containsExactly("事务异常回滚规则");
	}

	private static Topic topic(String code, String title) {
		return new Topic(new Domain(UUID.randomUUID(), "spring", "Spring", 40), code, title, TopicSource.BUILTIN, true);
	}

	private static ReviewPoint point(Topic topic, String title) {
		return new ReviewPoint(topic, title, 5, 4, 5, "next probe");
	}

	private static ReviewPoint duePoint(Topic topic, String title, LocalDate dueDate) {
		ReviewPoint point = point(topic, title);
		point.updateReviewProgress(
				BigDecimal.ONE,
				ReviewPointStatus.DUE,
				Instant.parse("2026-05-20T00:00:00Z"),
				dueDate.atStartOfDay().toInstant(ZoneOffset.UTC),
				1,
				0,
				List.of(),
				"next probe");
		return point;
	}

	private UserReviewUnitState activeState(ReviewPoint point, LocalDate dueDate) {
		UserReviewUnitState state = new UserReviewUnitState(user, point, Instant.parse("2026-05-20T00:00:00Z"));
		state.recordAttempt(
				ReviewAttemptResult.PARTIAL,
				Instant.parse("2026-05-20T00:00:00Z"),
				dueDate.atStartOfDay().toInstant(ZoneOffset.UTC));
		return state;
	}

	private ReviewWeaknessEvent weaknessEvent(ReviewPoint point, String category, String label) {
		UserReviewUnitState state = new UserReviewUnitState(user, point, Instant.parse("2026-06-03T00:00:00Z"));
		ReviewSession session = new ReviewSession(user, state, Instant.parse("2026-06-03T00:00:00Z"));
		return new ReviewWeaknessEvent(point, session, null, category, label, "evidence", 4);
	}
}

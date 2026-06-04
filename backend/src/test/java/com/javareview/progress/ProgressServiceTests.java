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
import com.javareview.reviewsession.ReviewSession;
import com.javareview.reviewsession.ReviewSessionRepository;
import com.javareview.reviewsession.ReviewSessionStatus;
import com.javareview.today.ReviewTask;
import com.javareview.today.ReviewTaskType;
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

	private ProgressService progressService;
	private User user;

	@BeforeEach
	void setUp() {
		progressService = new ProgressService(
				topicRepository,
				reviewPointRepository,
				weaknessEventRepository,
				reviewSessionRepository,
				Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
	}

	@Test
	void overviewReturnsDefaultsWhenNoScopeExists() {
		when(topicRepository.findAllWithDomain()).thenReturn(List.of());
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

		assertThat(progressService.domains()).isEmpty();
		assertThat(progressService.topics(null)).isEmpty();
		assertThat(progressService.weakPoints()).isEmpty();
		assertThat(progressService.dueReviewPoints()).isEmpty();
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
	void topicsExposeUncoveredStatusAndOpenWeaknessSummary() {
		Topic topic = topic("spring-transactions", "Spring 事务");
		ReviewPoint point = point(topic, "事务代理生效边界");
		ReviewWeaknessEvent event = weaknessEvent(point, "missing_boundary", "缺少事务失效边界");
		when(topicRepository.findAllWithDomain()).thenReturn(List.of(topic));
		when(reviewPointRepository.findByTopicIdIn(List.of(topic.getId()))).thenReturn(List.of(point));
		when(weaknessEventRepository.findByReviewPoint_IdIn(List.of(point.getId()))).thenReturn(List.of(event));

		var response = progressService.topics(null);

		assertThat(response).hasSize(1);
		assertThat(response.getFirst().status()).isEqualTo("uncovered");
		assertThat(response.getFirst().openWeaknessCount()).isEqualTo(1);
		assertThat(response.getFirst().weakPointSummary()).containsExactly("缺少事务失效边界");
		verify(weaknessEventRepository).findByReviewPoint_IdIn(List.of(point.getId()));
	}

	private static Topic topic(String code, String title) {
		return new Topic(new Domain(UUID.randomUUID(), "spring", "Spring", 40), code, title, TopicSource.BUILTIN, true);
	}

	private static ReviewPoint point(Topic topic, String title) {
		return new ReviewPoint(topic, title, 5, 4, 5, "next probe");
	}

	private ReviewWeaknessEvent weaknessEvent(ReviewPoint point, String category, String label) {
		ReviewTask task = new ReviewTask(
				user,
				point,
				LocalDate.of(2026, 6, 3),
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		ReviewSession session = new ReviewSession(user, task, Instant.parse("2026-06-03T00:00:00Z"));
		return new ReviewWeaknessEvent(point, session, null, category, label, "evidence", 4);
	}
}

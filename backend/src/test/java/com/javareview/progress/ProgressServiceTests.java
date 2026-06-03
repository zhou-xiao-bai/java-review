package com.javareview.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.reviewsession.ReviewSessionRepository;
import com.javareview.reviewsession.ReviewSessionStatus;
import com.javareview.topic.TopicRepository;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTests {

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private ReviewPointRepository reviewPointRepository;

	@Mock
	private ReviewSessionRepository reviewSessionRepository;

	private ProgressService progressService;
	private User user;

	@BeforeEach
	void setUp() {
		progressService = new ProgressService(
				topicRepository,
				reviewPointRepository,
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
	}

	@Test
	void listsReturnEmptyDefaultsWhenNoScopeExists() {
		when(topicRepository.findAllWithDomain()).thenReturn(List.of());

		assertThat(progressService.domains()).isEmpty();
		assertThat(progressService.topics(null)).isEmpty();
		assertThat(progressService.weakPoints()).isEmpty();
		assertThat(progressService.dueReviewPoints()).isEmpty();
	}
}

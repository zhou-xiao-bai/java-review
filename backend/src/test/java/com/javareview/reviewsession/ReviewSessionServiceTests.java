package com.javareview.reviewsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.llm.LlmClient;
import com.javareview.llm.LlmResult;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.ReviewTask;
import com.javareview.today.ReviewTaskRepository;
import com.javareview.today.ReviewTaskStatus;
import com.javareview.today.ReviewTaskType;
import com.javareview.topic.Domain;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class ReviewSessionServiceTests {

	private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

	@Mock
	private ReviewTaskRepository reviewTaskRepository;

	@Mock
	private ReviewSessionRepository reviewSessionRepository;

	@Mock
	private ReviewTurnRepository reviewTurnRepository;

	@Mock
	private SettingsService settingsService;

	@Mock
	private LlmClient llmClient;

	private ReviewSessionService reviewSessionService;
	private User user;
	private ReviewTask task;
	private ReviewSession session;
	private ReviewPoint point;

	@BeforeEach
	void setUp() {
		reviewSessionService = new ReviewSessionService(
				reviewTaskRepository,
				reviewSessionRepository,
				reviewTurnRepository,
				settingsService,
				llmClient,
				new ObjectMapper().findAndRegisterModules(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		Topic topic = new Topic(new Domain(java.util.UUID.randomUUID(), "spring", "Spring", 40),
				"spring-transactions", "Spring 事务", TopicSource.BUILTIN, true);
		point = new ReviewPoint(topic, "事务代理生效边界", 5, 4, 5, "next probe");
		task = new ReviewTask(user, point, LocalDate.of(2026, 6, 3), ReviewTaskType.NEW, java.math.BigDecimal.TEN, 10);
		session = new ReviewSession(user, task, NOW);
	}

	@Test
	void startGeneratesInitialQuestionFromReviewPointWithLlm() {
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId())).thenReturn(Optional.of(task));
		when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> List.of(new ReviewTurn(new ReviewSession(user, task, NOW), ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理在什么情况下会失效？")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("事务代理在什么情况下会失效？"));

		var response = reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(task.getId()));

		assertThat(response.turns()).hasSize(1);
		assertThat(response.turns().getFirst().content()).isEqualTo("事务代理在什么情况下会失效？");
		verify(llmClient).complete(eq(settings), any(), org.mockito.ArgumentMatchers.contains("复习点：事务代理生效边界"));
	}

	@Test
	void unknownClosesSessionAndUpdatesReviewPointAsUnstable() {
		stubActiveSession();

		var response = reviewSessionService.unknown(user, session.getId());

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.COMPLETED);
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.UNSTABLE);
		assertThat(point.getReviewCount()).isEqualTo(1);
		assertThat(point.getWrongCount()).isEqualTo(1);
	}

	@Test
	void skipDoesNotUpdateReviewPointMastery() {
		stubActiveSession();
		var originalMastery = point.getMastery();

		var response = reviewSessionService.skip(user, session.getId());

		assertThat(response.status()).isEqualTo("abandoned");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.SKIPPED);
		assertThat(point.getMastery()).isEqualByComparingTo(originalMastery);
		assertThat(point.getReviewCount()).isZero();
		verify(settingsService, never()).findOrDefault(any());
	}

	private void stubActiveSession() {
		when(reviewSessionRepository.findByIdAndUserIdWithTask(session.getId(), user.getId())).thenReturn(Optional.of(session));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(new ArrayList<>());
	}
}

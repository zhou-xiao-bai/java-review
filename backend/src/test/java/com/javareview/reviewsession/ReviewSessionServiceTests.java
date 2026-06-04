package com.javareview.reviewsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.llm.LlmClient;
import com.javareview.llm.LlmResult;
import com.javareview.reviewpoint.ReviewWeaknessEvent;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.reviewpoint.ReviewWeaknessEventRepository;
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
	private ReviewWeaknessEventRepository weaknessEventRepository;

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
				weaknessEventRepository,
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
	void startFailsWhenLlmDoesNotGenerateQuestion() {
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId())).thenReturn(Optional.of(task));
		when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.failure("连接失败"));

		assertThatThrownBy(() -> reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(task.getId())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AI 题目生成失败")
				.hasMessageContaining("连接失败");
	}

	@Test
	void startRejectsRemovedTask() {
		task.removeFromToday(NOW);
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId())).thenReturn(Optional.of(task));

		assertThatThrownBy(() -> reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(task.getId())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Review task has been removed from today's plan.");

		verify(reviewSessionRepository, never()).save(any());
		verify(settingsService, never()).findOrDefault(any());
	}

	@Test
	void startRestoresExistingActiveSessionForInProgressTask() {
		task.start();
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId())).thenReturn(Optional.of(task));
		when(reviewSessionRepository.findActiveByTaskIdAndUserId(task.getId(), user.getId())).thenReturn(List.of(session));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(new ArrayList<>());

		var response = reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(task.getId()));

		assertThat(response.id()).isEqualTo(session.getId());
		verify(reviewSessionRepository, never()).save(any());
		verify(settingsService, never()).findOrDefault(any());
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

	@Test
	void clarifyCallsLlmAndStoresQuestionIntentOnly() {
		stubActiveSession();
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), org.mockito.ArgumentMatchers.contains("用户的题意追问：这题想问什么？")))
				.thenReturn(LlmResult.success("这题在确认你是否理解事务代理边界。按意图、维度、回答顺序组织即可，不展开答案。"));

		reviewSessionService.clarify(user, session.getId(), new ReviewSessionDtos.ClarifyRequest("这题想问什么？"));

		ArgumentCaptor<ReviewTurn> turnCaptor = ArgumentCaptor.forClass(ReviewTurn.class);
		verify(reviewTurnRepository, times(2)).save(turnCaptor.capture());
		assertThat(turnCaptor.getAllValues())
				.extracting(ReviewTurn::getTurnType)
				.containsExactly(ReviewTurnType.CLARIFICATION, ReviewTurnType.CLARIFICATION);
		assertThat(turnCaptor.getAllValues().get(1).getContent()).contains("事务代理边界");
		verify(llmClient).complete(eq(settings), org.mockito.ArgumentMatchers.contains("不泄露标准答案"), any());
	}

	@Test
	void answerContinuesWithTargetedFollowUpAndStoresWeaknessSignals() {
		stubActiveSessionWithTurns(List.of(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("""
				{
				  "action": "follow_up",
				  "followUpQuestion": "请只补充 self-invocation 为什么会导致事务代理失效？",
				  "weakSignals": [
				    {"category": "missing_mechanism", "label": "缺少代理调用链路", "evidence": "只说了注解", "severity": 4}
				  ],
				  "evaluation": null
				}
				"""));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("加了 @Transactional 就会开启事务。"));

		assertThat(response.status()).isEqualTo("active");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
		ArgumentCaptor<ReviewTurn> turnCaptor = ArgumentCaptor.forClass(ReviewTurn.class);
		verify(reviewTurnRepository, times(2)).save(turnCaptor.capture());
		assertThat(turnCaptor.getAllValues().get(1).getTurnType()).isEqualTo(ReviewTurnType.FOLLOW_UP);
		assertThat(turnCaptor.getAllValues().get(1).getContent()).contains("self-invocation");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ReviewWeaknessEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
		verify(weaknessEventRepository).saveAll(eventCaptor.capture());
		assertThat(eventCaptor.getValue())
				.extracting(ReviewWeaknessEvent::getLabel)
				.containsExactly("缺少代理调用链路");
	}

	@Test
	void answerTreatsExplicitNoAnswerAsEvaluationWithoutCallingLlm() {
		stubActiveSessionWithTurns(List.of(
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？"),
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP, "请补充事务传播边界。")));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("不清楚"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.COMPLETED);
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.UNSTABLE);
		assertThat(point.getReviewCount()).isEqualTo(1);
		assertThat(point.getWrongCount()).isEqualTo(1);
		verify(settingsService, never()).findOrDefault(any());
		verify(llmClient, never()).complete(any(), any(), any());
		ArgumentCaptor<ReviewTurn> turnCaptor = ArgumentCaptor.forClass(ReviewTurn.class);
		verify(reviewTurnRepository, times(2)).save(turnCaptor.capture());
		assertThat(turnCaptor.getAllValues())
				.extracting(ReviewTurn::getTurnType)
				.containsExactly(ReviewTurnType.ANSWER, ReviewTurnType.EVALUATION);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ReviewWeaknessEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
		verify(weaknessEventRepository).saveAll(eventCaptor.capture());
		assertThat(eventCaptor.getValue().getFirst().getEvidence()).contains("不清楚");
	}

	@Test
	void answerEvaluatesWhenLlmRepeatsPreviousFollowUp() {
		String repeatedFollowUp = "你刚才的回答还比较短。请补充「事务代理生效边界」在生产问题中的排查路径，以及最容易被忽略的边界。";
		stubActiveSessionWithTurns(List.of(
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？"),
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP, repeatedFollowUp)));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("""
				{
				  "action": "follow_up",
				  "followUpQuestion": "你刚才的回答还比较短。请补充「事务代理生效边界」在生产问题中的排查路径，以及最容易被忽略的边界。",
				  "weakSignals": [
				    {"category": "missing_production", "label": "生产排查路径缺失", "evidence": "回答仍停留在会提交或回滚", "severity": 4}
				  ],
				  "evaluation": null
				}
				"""));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("initAccount 会回滚，外层会提交。"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.COMPLETED);
		assertThat(point.getWeakPoints()).containsExactly("生产排查路径缺失");
		ArgumentCaptor<ReviewTurn> turnCaptor = ArgumentCaptor.forClass(ReviewTurn.class);
		verify(reviewTurnRepository, times(2)).save(turnCaptor.capture());
		assertThat(turnCaptor.getAllValues())
				.extracting(ReviewTurn::getTurnType)
				.containsExactly(ReviewTurnType.ANSWER, ReviewTurnType.EVALUATION);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ReviewWeaknessEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
		verify(weaknessEventRepository).saveAll(eventCaptor.capture());
		assertThat(eventCaptor.getValue())
				.extracting(ReviewWeaknessEvent::getLabel)
				.containsExactly("生产排查路径缺失");
	}

	@Test
	void answerEvaluatesAndUpdatesReviewPointMasteryCard() {
		stubActiveSessionWithTurns(List.of(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("""
				{
				  "action": "evaluate",
				  "followUpQuestion": null,
				  "weakSignals": [],
				  "evaluation": {
				    "overallComment": "主线清楚，继续保持边界表达。",
				    "correctPoints": ["说明了代理调用"],
				    "missingPoints": [],
				    "inaccuratePoints": [],
				    "referenceAnswer": "先讲代理，再讲失效边界和排查。",
				    "score": {"conclusionAccuracy": 4, "mechanismExplanation": 4, "boundaryCases": 4, "transferApplication": 4, "overall": 4},
				    "weakSignals": [
				      {"category": "expression_gap", "label": "生产排查表达还可压缩", "evidence": "排查路径较长", "severity": 2}
				    ],
				    "weakPoints": ["生产排查表达还可压缩"],
				    "nextProbe": "追问事务代理失效的排查入口。",
				    "nextStatus": "stable",
				    "masteryCard": {
				      "oneSentence": "事务是否生效取决于调用是否经过代理和回滚规则是否命中。",
				      "answerSkeleton": ["结论", "代理链路", "失效边界", "排查入口"],
				      "mustRemember": ["self-invocation 不经过代理", "异常类型影响回滚"],
				      "nextProbe": "追问事务代理失效的排查入口。"
				    }
				  }
				}
				"""));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("事务通过代理增强，self-invocation 不经过代理，所以会失效。"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.COMPLETED);
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.STABLE);
		assertThat(point.getMastery()).isEqualByComparingTo(BigDecimal.valueOf(4));
		assertThat(point.getMasteryCard()).isNotNull();
		assertThat(point.getMasteryCard().oneSentence()).contains("调用是否经过代理");
		assertThat(point.getWeakPoints()).containsExactly("生产排查表达还可压缩");
	}

	private void stubActiveSession() {
		stubActiveSessionWithTurns(new ArrayList<>());
	}

	private void stubActiveSessionWithTurns(List<ReviewTurn> turns) {
		when(reviewSessionRepository.findByIdAndUserIdWithTask(session.getId(), user.getId())).thenReturn(Optional.of(session));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(turns);
	}
}

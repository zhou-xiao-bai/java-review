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
import com.javareview.today.ReviewPriorityService;
import com.javareview.reviewunit.QuestionVariant;
import com.javareview.reviewunit.QuestionVariantSelectionService;
import com.javareview.reviewunit.QuestionVariantType;
import com.javareview.reviewunit.ReviewAttemptRepository;
import com.javareview.reviewunit.ReviewAttemptResult;
import com.javareview.reviewunit.TodayReviewAction;
import com.javareview.reviewunit.TodayReviewActionRepository;
import com.javareview.reviewunit.TodayReviewActionType;
import com.javareview.reviewunit.UserReviewUnitState;
import com.javareview.reviewunit.UserReviewUnitStateRepository;
import com.javareview.reviewunit.UserReviewUnitStatus;
import com.javareview.topic.Domain;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class ReviewSessionServiceTests {

	private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

	@Mock
	private UserReviewUnitStateRepository reviewUnitStateRepository;

	@Mock
	private QuestionVariantSelectionService questionVariantSelectionService;

	@Mock
	private ReviewAttemptRepository reviewAttemptRepository;

	@Mock
	private TodayReviewActionRepository todayReviewActionRepository;

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
	private UserReviewUnitState state;
	private ReviewSession session;
	private ReviewPoint point;

	@BeforeEach
	void setUp() {
		reviewSessionService = new ReviewSessionService(
				reviewUnitStateRepository,
				questionVariantSelectionService,
				reviewAttemptRepository,
				todayReviewActionRepository,
				reviewSessionRepository,
				reviewTurnRepository,
				weaknessEventRepository,
				settingsService,
				llmClient,
				new ObjectMapper().findAndRegisterModules(),
				new ReviewPriorityService(Clock.fixed(NOW, ZoneOffset.UTC)),
				Clock.fixed(NOW, ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		Topic topic = new Topic(new Domain(java.util.UUID.randomUUID(), "spring", "Spring", 40),
				"spring-transactions", "Spring 事务", TopicSource.BUILTIN, true);
		point = new ReviewPoint(topic, "事务代理生效边界", 5, 4, 5, "next probe");
		state = new UserReviewUnitState(user, point, NOW);
		session = new ReviewSession(user, state, NOW);
	}

	@Test
	void startGeneratesInitialQuestionFromReviewPointWithLlm() {
		when(reviewUnitStateRepository.findByIdAndUserIdWithUnit(state.getId(), user.getId())).thenReturn(Optional.of(state));
		when(questionVariantSelectionService.selectFor(user, point)).thenReturn(Optional.empty());
		when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenAnswer(invocation -> List.of(new ReviewTurn(new ReviewSession(user, state, NOW), ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理在什么情况下会失效？")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("事务代理在什么情况下会失效？"));

		var response = reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(state.getId()));

		assertThat(response.turns()).hasSize(1);
		assertThat(response.turns().getFirst().content()).isEqualTo("事务代理在什么情况下会失效？");
		verify(llmClient).complete(eq(settings), any(), org.mockito.ArgumentMatchers.contains("复习点：事务代理生效边界"));
	}

	@Test
	void startSelectsQuestionVariantAndStoresItOnSession() {
		QuestionVariant variant = new QuestionVariant(
				point,
				"self-invocation 场景题",
				"请分析 self-invocation 为什么会导致事务代理失效。",
				"验证代理调用链路和自调用边界",
				4,
				QuestionVariantType.SCENARIO);
		when(reviewUnitStateRepository.findByIdAndUserIdWithUnit(state.getId(), user.getId())).thenReturn(Optional.of(state));
		when(questionVariantSelectionService.selectFor(user, point)).thenReturn(Optional.of(variant));
		when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of(new ReviewTurn(new ReviewSession(user, state, variant, NOW), ReviewTurnRole.AI, ReviewTurnType.QUESTION, "请分析 self-invocation 为什么会导致事务代理失效。")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("请分析 self-invocation 为什么会导致事务代理失效。"));

		var response = reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(state.getId()));

		ArgumentCaptor<ReviewSession> sessionCaptor = ArgumentCaptor.forClass(ReviewSession.class);
		verify(reviewSessionRepository).save(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getQuestionVariant()).isSameAs(variant);
		assertThat(response.questionVariantId()).isEqualTo(variant.getId());
		assertThat(response.questionVariantTitle()).isEqualTo("self-invocation 场景题");
		verify(llmClient).complete(
				eq(settings),
				any(),
				org.mockito.ArgumentMatchers.contains("题目变体：self-invocation 场景题"));
	}

	@Test
	void startFailsWhenLlmDoesNotGenerateQuestion() {
		when(reviewUnitStateRepository.findByIdAndUserIdWithUnit(state.getId(), user.getId())).thenReturn(Optional.of(state));
		when(questionVariantSelectionService.selectFor(user, point)).thenReturn(Optional.empty());
		when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.failure("连接失败"));

		assertThatThrownBy(() -> reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(state.getId())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AI 题目生成失败")
				.hasMessageContaining("连接失败");
	}

	@Test
	void startRejectsArchivedReviewUnitState() {
		state.archive(NOW);
		when(reviewUnitStateRepository.findByIdAndUserIdWithUnit(state.getId(), user.getId())).thenReturn(Optional.of(state));

		assertThatThrownBy(() -> reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(state.getId())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Review unit is not startable.");

		verify(reviewSessionRepository, never()).save(any());
		verify(settingsService, never()).findOrDefault(any());
	}

	@Test
	void startRestoresExistingActiveSessionForInProgressTask() {
		when(reviewUnitStateRepository.findByIdAndUserIdWithUnit(state.getId(), user.getId())).thenReturn(Optional.of(state));
		when(reviewSessionRepository.findActiveByStateIdAndUserId(state.getId(), user.getId())).thenReturn(List.of(session));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(new ArrayList<>());

		var response = reviewSessionService.start(user, new ReviewSessionDtos.StartReviewSessionRequest(state.getId()));

		assertThat(response.id()).isEqualTo(session.getId());
		verify(reviewSessionRepository, never()).save(any());
		verify(settingsService, never()).findOrDefault(any());
	}

	@Test
	void unknownClosesSessionAndUpdatesReviewPointAsUnstable() {
		stubActiveSession();
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any()))
				.thenReturn(LlmResult.success(noAnswerEvaluationJson("用户标记不会。", 4)));

		var response = reviewSessionService.unknown(user, session.getId());

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.ACTIVE);
		assertThat(state.getLastResult()).isEqualTo(ReviewAttemptResult.POOR);
		assertThat(state.getNextReviewAt()).isEqualTo(NOW.plusSeconds(86_400));
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.UNSTABLE);
		assertThat(point.getMastery()).isEqualByComparingTo(BigDecimal.valueOf(2));
		assertThat(point.getReviewCount()).isEqualTo(1);
		assertThat(point.getWrongCount()).isEqualTo(1);
		assertThat(response.evaluation().correctPoints()).isEmpty();
		assertThat(response.evaluation().score().overall()).isEqualByComparingTo(BigDecimal.valueOf(2));
		assertThat(response.evaluation().masteryCard().oneSentence()).contains("Spring 代理对象");
		assertThat(response.evaluation().nextProbe()).contains("继续考察");
		assertThat(response.evaluation().nextProbe()).doesNotStartWith("请");
		assertThat(response.evaluation().masteryCard().nextProbe()).contains("继续考察");
		assertThat(response.evaluation().corrections()).isNotEmpty();
		assertThat(response.evaluation().corrections())
				.anySatisfy(correction -> assertThat(correction.correctAnswer()).contains("Spring", "代理"));
		assertThat(response.reviewPlanExplanation().scheduleRule()).isEqualTo("明天复习");
		assertThat(response.reviewPlanExplanation().priorityScore()).isPositive();
		assertThat(response.reviewPlanExplanation().priorityFactors())
				.anySatisfy(factor -> assertThat(factor.label()).isEqualTo("知识点重要度"))
				.anySatisfy(factor -> assertThat(factor.label()).isEqualTo("知识点困难度"));
		verify(llmClient).complete(
				eq(settings),
				org.mockito.ArgumentMatchers.contains("复习教练"),
				org.mockito.ArgumentMatchers.contains("用户证据：用户标记不会。"));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ReviewWeaknessEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
		verify(weaknessEventRepository).saveAll(eventCaptor.capture());
		assertThat(eventCaptor.getValue().getFirst().getTurn().getTurnType()).isEqualTo(ReviewTurnType.UNKNOWN);
		assertThat(eventCaptor.getValue().getFirst().getEvidence()).contains("用户标记不会。");
	}

	@Test
	void skipDoesNotUpdateReviewPointMastery() {
		stubActiveSession();
		var originalMastery = point.getMastery();
		when(todayReviewActionRepository.save(any(TodayReviewAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var response = reviewSessionService.skip(user, session.getId());

		assertThat(response.status()).isEqualTo("abandoned");
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.PENDING_FIRST_REVIEW);
		assertThat(point.getMastery()).isEqualByComparingTo(originalMastery);
		assertThat(point.getReviewCount()).isZero();
		verify(settingsService, never()).findOrDefault(any());
		ArgumentCaptor<TodayReviewAction> actionCaptor = ArgumentCaptor.forClass(TodayReviewAction.class);
		verify(todayReviewActionRepository).save(actionCaptor.capture());
		assertThat(actionCaptor.getValue().getActionType()).isEqualTo(TodayReviewActionType.DISMISS_TODAY);
		assertThat(actionCaptor.getValue().getReviewUnit()).isSameAs(point);
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
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.PENDING_FIRST_REVIEW);
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
	void answerForcesFollowUpWhenEvaluationStillHasBlockingWeakness() {
		stubActiveSessionWithTurns(List.of(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success("""
				{
				  "action": "evaluate",
				  "followUpQuestion": null,
				  "weakSignals": [],
				  "evaluation": {
				    "overallComment": "主线有覆盖，但事务自调用边界没有验证。",
				    "correctPoints": ["提到了代理增强"],
				    "missingPoints": ["没有说明 self-invocation 为什么绕过代理"],
				    "inaccuratePoints": [],
				    "referenceAnswer": "先讲代理对象，再讲自调用和排查。",
				    "score": {"conclusionAccuracy": 4, "mechanismExplanation": 3, "boundaryCases": 2, "transferApplication": 2, "overall": 3},
				    "weakSignals": [
				      {"category": "missing_boundary", "label": "事务自调用边界未验证", "evidence": "只说加注解会生效", "severity": 4}
				    ],
				    "weakPoints": ["事务自调用边界未验证"],
				    "nextProbe": "追问 self-invocation 为什么绕过事务代理。",
				    "nextStatus": "unstable",
				    "masteryCard": {
				      "oneSentence": "事务生效取决于调用是否经过代理。",
				      "answerSkeleton": ["结论", "代理链路", "自调用边界", "排查入口"],
				      "mustRemember": ["self-invocation 不经过代理"],
				      "nextProbe": "追问 self-invocation 为什么绕过事务代理。"
				    }
				  }
				}
				"""));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("事务是通过代理增强的，加了 @Transactional 就会生效。"));

		assertThat(response.status()).isEqualTo("active");
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.PENDING_FIRST_REVIEW);
		ArgumentCaptor<ReviewTurn> turnCaptor = ArgumentCaptor.forClass(ReviewTurn.class);
		verify(reviewTurnRepository, times(2)).save(turnCaptor.capture());
		assertThat(turnCaptor.getAllValues().get(1).getTurnType()).isEqualTo(ReviewTurnType.FOLLOW_UP);
		assertThat(turnCaptor.getAllValues().get(1).getContent()).contains("事务自调用边界未验证");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ReviewWeaknessEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
		verify(weaknessEventRepository).saveAll(eventCaptor.capture());
		assertThat(eventCaptor.getValue())
				.extracting(ReviewWeaknessEvent::getLabel)
				.containsExactly("事务自调用边界未验证");
	}

	@Test
	void answerTreatsExplicitNoAnswerAsLlmGeneratedGapReview() {
		stubActiveSessionWithTurns(List.of(
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, "事务代理什么时候失效？"),
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP, "请补充事务传播边界。")));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any()))
				.thenReturn(LlmResult.success(noAnswerEvaluationJson("用户表示不清楚：不清楚", 5)));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("不清楚"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.ACTIVE);
		assertThat(state.getLastResult()).isEqualTo(ReviewAttemptResult.POOR);
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.UNSTABLE);
		assertThat(point.getReviewCount()).isEqualTo(1);
		assertThat(point.getWrongCount()).isEqualTo(1);
		assertThat(response.evaluation().correctPoints()).isEmpty();
		assertThat(response.evaluation().missingPoints()).contains("没有说明事务是否生效取决于调用是否进入 Spring 代理对象");
		assertThat(response.evaluation().score().overall()).isEqualByComparingTo(BigDecimal.valueOf(2));
		assertThat(response.evaluation().masteryCard().oneSentence()).contains("Spring 代理对象");
		assertThat(response.evaluation().nextProbe()).contains("继续考察");
		assertThat(response.evaluation().nextProbe()).doesNotStartWith("请");
		assertThat(response.evaluation().masteryCard().nextProbe()).contains("继续考察");
		assertThat(response.evaluation().corrections()).isNotEmpty();
		assertThat(response.evaluation().corrections())
				.anySatisfy(correction -> assertThat(correction.userIssue()).contains("Spring 代理对象"));
		verify(llmClient).complete(
				eq(settings),
				org.mockito.ArgumentMatchers.contains("复习教练"),
				org.mockito.ArgumentMatchers.contains("用户证据：用户表示不清楚：不清楚"));
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
	void answerReplacesGenericNoAnswerReviewWithCacheConsistencyFallback() {
		Topic topic = new Topic(new Domain(java.util.UUID.randomUUID(), "cache", "缓存", 30),
				"cache-consistency", "缓存一致性", TopicSource.BUILTIN, true);
		point = new ReviewPoint(topic, "双写不一致窗口分析", 5, 5, 5, "说明旧值回写窗口。");
		state = new UserReviewUnitState(user, point, NOW);
		session = new ReviewSession(user, state, NOW);
		String question = "在一个 Java 电商系统中，商品详情使用 Redis 缓存，数据库是 MySQL。更新商品价格时采用“先更新数据库，再删除缓存”的方案；请分析在高并发场景下，这种双写策略仍然可能出现哪些不一致窗口？";
		stubActiveSessionWithTurns(List.of(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, question)));
		UserSettings settings = new UserSettings(user);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		when(llmClient.complete(eq(settings), any(), any())).thenReturn(LlmResult.success(genericNoAnswerEvaluationJson("用户表示不清楚：不会")));

		var response = reviewSessionService.answer(
				user,
				session.getId(),
				new ReviewSessionDtos.SubmitAnswerRequest("不会"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(response.evaluation().correctPoints()).isEmpty();
		assertThat(response.evaluation().score().overall()).isEqualByComparingTo(BigDecimal.valueOf(1.5));
		assertThat(response.evaluation().overallComment()).contains("写库、删缓存、并发读回源、旧值回写");
		assertThat(response.evaluation().missingPoints())
				.anySatisfy(point -> assertThat(point).contains("先更新 MySQL、再删除 Redis"))
				.anySatisfy(point -> assertThat(point).contains("TTL"))
				.anySatisfy(point -> assertThat(point).contains("旧值回填"));
		assertThat(response.evaluation().corrections())
				.anySatisfy(correction -> {
					assertThat(correction.userIssue()).contains("删除 Redis 失败");
					assertThat(correction.correctAnswer()).contains("TTL", "补偿删除");
				})
				.anySatisfy(correction -> {
					assertThat(correction.userIssue()).contains("旧值回填");
					assertThat(correction.correctAnswer()).contains("旧值", "Redis");
				});
		assertThat(response.evaluation().referenceAnswer()).contains("MySQL", "Redis", "TTL", "补偿删除");
		assertThat(response.evaluation().masteryCard().oneSentence()).contains("旧缓存还能存活多久");
		assertThat(response.evaluation().masteryCard().answerSkeleton()).anySatisfy(item -> assertThat(item).contains("写 MySQL", "删除 Redis"));
		assertThat(response.evaluation().weakSignals())
				.extracting(ReviewEvaluation.WeaknessSignal::label)
				.contains("Redis/MySQL 双写时序窗口不会分析");
		assertThat(point.getMasteryCard().oneSentence()).contains("旧缓存还能存活多久");
		assertThat(point.getWeakPoints()).contains("Redis/MySQL 双写时序窗口不会分析");
	}

	@Test
	void getRepairsStoredGenericNoAnswerReviewForCacheConsistencySession() throws Exception {
		Topic topic = new Topic(new Domain(java.util.UUID.randomUUID(), "cache", "缓存", 30),
				"cache-consistency", "缓存一致性", TopicSource.BUILTIN, true);
		point = new ReviewPoint(topic, "双写不一致窗口分析", 5, 5, 5, "说明旧值回写窗口。");
		state = new UserReviewUnitState(user, point, NOW);
		session = new ReviewSession(user, state, NOW);
		ReviewEvaluation storedEvaluation = new ObjectMapper().findAndRegisterModules()
				.readValue(genericNoAnswerEvaluationJson("用户标记不会。"), ReviewEvaluation.class);
		session.evaluate(storedEvaluation, NOW.plusSeconds(60));
		String question = "在一个 Java 电商系统中，商品详情使用 Redis 缓存，数据库是 MySQL。更新商品价格时采用“先更新数据库，再删除缓存”的方案；请分析在高并发场景下，这种双写策略仍然可能出现哪些不一致窗口？";
		when(reviewSessionRepository.findByIdAndUserIdWithUnit(session.getId(), user.getId())).thenReturn(Optional.of(session));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(
				new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, question),
				new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.UNKNOWN, "不会")));

		var response = reviewSessionService.get(user, session.getId());

		assertThat(response.evaluation().overallComment()).contains("写库、删缓存、并发读回源、旧值回写");
		assertThat(response.summary()).contains("写库、删缓存、并发读回源、旧值回写");
		assertThat(response.evaluation().missingPoints())
				.noneMatch(item -> item.contains("核心执行链路") || item.contains("排查步骤"))
				.anySatisfy(item -> assertThat(item).contains("旧值回填"));
		assertThat(response.evaluation().referenceAnswer()).contains("MySQL", "Redis", "TTL");
		assertThat(response.evaluation().corrections())
				.anySatisfy(correction -> assertThat(correction.correctAnswer()).contains("Redis", "TTL"))
				.anySatisfy(correction -> assertThat(correction.correctAnswer()).contains("旧值", "Redis"));
		assertThat(response.evaluation().masteryCard().answerSkeleton()).anySatisfy(item -> assertThat(item).contains("写 MySQL", "删除 Redis"));
		verify(settingsService, never()).findOrDefault(any());
		verify(llmClient, never()).complete(any(), any(), any());
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
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.ACTIVE);
		assertThat(state.getLastResult()).isEqualTo(ReviewAttemptResult.POOR);
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
		assertThat(state.getStatus()).isEqualTo(UserReviewUnitStatus.ACTIVE);
		assertThat(state.getLastResult()).isEqualTo(ReviewAttemptResult.PARTIAL);
		assertThat(point.getStatus()).isEqualTo(ReviewPointStatus.STABLE);
		assertThat(point.getMastery()).isEqualByComparingTo(BigDecimal.valueOf(4));
		assertThat(point.getMasteryCard()).isNotNull();
		assertThat(point.getMasteryCard().oneSentence()).contains("调用是否经过代理");
		assertThat(point.getWeakPoints()).containsExactly("生产排查表达还可压缩");
		assertThat(response.evaluation().corrections()).isNotEmpty();
		assertThat(response.evaluation().corrections())
				.anySatisfy(correction -> assertThat(correction.correctAnswer()).contains("代理"));
	}

	private void stubActiveSession() {
		stubActiveSessionWithTurns(new ArrayList<>());
	}

	private void stubActiveSessionWithTurns(List<ReviewTurn> turns) {
		when(reviewSessionRepository.findByIdAndUserIdWithUnit(session.getId(), user.getId())).thenReturn(Optional.of(session));
		when(reviewTurnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(turns);
	}

	private static String noAnswerEvaluationJson(String evidence, int score) {
		return """
				{
				  "overallComment": "本题没有形成有效作答，应先补事务代理是否经过 Spring 代理对象这条主链路。",
				  "correctPoints": ["模型误判的正确点会被后端清空"],
				  "missingPoints": [
				    "没有说明事务是否生效取决于调用是否进入 Spring 代理对象",
				    "没有说明 self-invocation 会绕过代理导致 @Transactional 不生效",
				    "没有给出从代理对象、异常类型和事务日志定位问题的入口"
				  ],
				  "inaccuratePoints": [],
				  "referenceAnswer": "事务代理是否生效，核心看调用有没有经过 Spring 生成的代理对象；外部调用进入代理后，拦截器才能开启、提交或回滚事务。self-invocation 是同一个对象内部方法调用，不经过代理，所以注解可能不生效；另外 private/final 方法、异常类型不匹配、传播行为配置都会影响结果。生产排查时先看调用入口拿到的是不是代理对象，再看事务日志、异常类型和传播配置。",
				  "score": {"conclusionAccuracy": %d, "mechanismExplanation": %d, "boundaryCases": %d, "transferApplication": %d, "overall": %d},
				  "weakSignals": [
				    {"category": "unknown", "label": "事务代理主链路未形成", "evidence": "%s", "severity": 5}
				  ],
				  "weakPoints": ["事务代理主链路未形成"],
				  "nextProbe": "请独立说明 self-invocation 为什么会绕过 Spring 事务代理。",
				  "nextStatus": "stable",
				  "masteryCard": {
				    "oneSentence": "事务是否生效首先看调用有没有进入 Spring 代理对象。",
				    "answerSkeleton": [
				      "先判断调用入口拿到的是目标对象还是 Spring 代理对象",
				      "再说明事务拦截器如何围绕方法调用开启、提交或回滚事务",
				      "最后用 self-invocation、异常类型和传播行为说明失效边界"
				    ],
				    "mustRemember": [
				      "self-invocation 是对象内部调用，不会再次进入代理拦截器",
				      "只看 @Transactional 注解不够，还要看调用入口、异常类型和传播配置"
				    ],
				    "nextProbe": "请独立说明 self-invocation 为什么会绕过 Spring 事务代理。"
				  }
				}
				""".formatted(score, score, score, score, score, evidence);
	}

	private static String genericNoAnswerEvaluationJson(String evidence) {
		return """
				{
				  "overallComment": "本题没有形成有效作答，需要先补齐核心链路和关键边界。",
				  "correctPoints": ["这类泛化正确点会被清空"],
				  "missingPoints": [
				    "没有说出核心执行链路",
				    "没有说明关键边界、失败条件或反例",
				    "没有形成可用于面试复述的排查步骤"
				  ],
				  "inaccuratePoints": [],
				  "referenceAnswer": "先用一句话定义主题，再按核心流程、关键分支、失效边界、排查入口组织两分钟回答。",
				  "score": {"conclusionAccuracy": 1.5, "mechanismExplanation": 1.5, "boundaryCases": 1.5, "transferApplication": 1.5, "overall": 1.5},
				  "weakSignals": [
				    {"category": "unknown", "label": "机制边界仍需复验", "evidence": "%s", "severity": 4}
				  ],
				  "weakPoints": ["机制边界仍需复验"],
				  "nextProbe": "要求独立说明核心链路、关键边界和排查步骤。",
				  "nextStatus": "unstable",
				  "masteryCard": {
				    "oneSentence": "需要用结论、机制、边界和排查路径四段式表达。",
				    "answerSkeleton": ["先给结论，说明核心机制是什么", "再讲关键调用链路或数据流", "补充常见失效边界和反例", "最后落到生产排查入口和工程取舍"],
				    "mustRemember": ["不要只背概念，要讲清触发条件", "边界场景通常比定义更能拉开差距"],
				    "nextProbe": "要求说明核心链路、失效边界和排查步骤。"
				  }
				}
				""".formatted(evidence);
	}
}

package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javareview.auth.User;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.llm.LlmClient;
import com.javareview.llm.LlmResult;
import com.javareview.reviewpoint.MasteryCard;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.reviewpoint.ReviewWeaknessEvent;
import com.javareview.reviewpoint.ReviewWeaknessEventRepository;
import com.javareview.reviewunit.QuestionVariant;
import com.javareview.reviewunit.QuestionVariantSelectionService;
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
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.ReviewPriorityService;
import com.javareview.reviewsession.ReviewEvaluation.Correction;
import com.javareview.reviewsession.ReviewEvaluation.ReviewScore;
import com.javareview.reviewsession.ReviewEvaluation.WeaknessSignal;
import com.javareview.reviewsession.ReviewSessionDtos.ClarifyRequest;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewPlanExplanation;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewPlanFactor;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewSessionResponse;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewTurnResponse;
import com.javareview.reviewsession.ReviewSessionDtos.StartReviewSessionRequest;
import com.javareview.reviewsession.ReviewSessionDtos.SubmitAnswerRequest;

@Service
public class ReviewSessionService {

	private static final int DEFAULT_MAX_FOLLOW_UPS = 1;
	private static final int CORE_MAX_FOLLOW_UPS = 2;
	private static final int HARD_MAX_FOLLOW_UPS = 3;
	private static final int BLOCKING_WEAKNESS_SEVERITY = 3;
	private static final BigDecimal LOW_MASTERY_THRESHOLD = BigDecimal.valueOf(3.5);
	private static final BigDecimal NEAR_PERFECT_SCORE_THRESHOLD = BigDecimal.valueOf(4.8);

	private final UserReviewUnitStateRepository reviewUnitStateRepository;
	private final QuestionVariantSelectionService questionVariantSelectionService;
	private final ReviewAttemptRepository reviewAttemptRepository;
	private final TodayReviewActionRepository todayReviewActionRepository;
	private final ReviewSessionRepository reviewSessionRepository;
	private final ReviewTurnRepository reviewTurnRepository;
	private final ReviewWeaknessEventRepository weaknessEventRepository;
	private final SettingsService settingsService;
	private final LlmClient llmClient;
	private final ObjectMapper objectMapper;
	private final ReviewPriorityService reviewPriorityService;
	private final Clock clock;

	public ReviewSessionService(
			UserReviewUnitStateRepository reviewUnitStateRepository,
			QuestionVariantSelectionService questionVariantSelectionService,
			ReviewAttemptRepository reviewAttemptRepository,
			TodayReviewActionRepository todayReviewActionRepository,
			ReviewSessionRepository reviewSessionRepository,
			ReviewTurnRepository reviewTurnRepository,
			ReviewWeaknessEventRepository weaknessEventRepository,
			SettingsService settingsService,
			LlmClient llmClient,
			ObjectMapper objectMapper,
			ReviewPriorityService reviewPriorityService,
			Clock clock) {
		this.reviewUnitStateRepository = reviewUnitStateRepository;
		this.questionVariantSelectionService = questionVariantSelectionService;
		this.reviewAttemptRepository = reviewAttemptRepository;
		this.todayReviewActionRepository = todayReviewActionRepository;
		this.reviewSessionRepository = reviewSessionRepository;
		this.reviewTurnRepository = reviewTurnRepository;
		this.weaknessEventRepository = weaknessEventRepository;
		this.settingsService = settingsService;
		this.llmClient = llmClient;
		this.objectMapper = objectMapper;
		this.reviewPriorityService = reviewPriorityService;
		this.clock = clock;
	}

	@Transactional
	public ReviewSessionResponse start(User user, StartReviewSessionRequest request) {
		UserReviewUnitState state = requireReviewUnitState(user, request.reviewUnitStateId());
		if (state.getStatus() == UserReviewUnitStatus.ARCHIVED || state.getStatus() == UserReviewUnitStatus.NOT_FOR_ME) {
			throw new IllegalStateException("Review unit is not startable.");
		}
		List<ReviewSession> activeSessions = reviewSessionRepository.findActiveByStateIdAndUserId(state.getId(), user.getId());
		if (!activeSessions.isEmpty()) {
			return toResponse(activeSessions.getFirst());
		}
		QuestionVariant questionVariant = questionVariantSelectionService
				.selectFor(user, state.getReviewUnit())
				.orElse(null);
		ReviewSession session = reviewSessionRepository.save(new ReviewSession(user, state, questionVariant, Instant.now(clock)));
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION,
				generateInitialQuestion(user, state.getReviewUnit(), questionVariant)));
		return toResponse(session);
	}

	@Transactional(readOnly = true)
	public ReviewSessionResponse get(User user, UUID sessionId) {
		return toResponse(requireSession(user, sessionId));
	}

	@Transactional
	public ReviewSessionResponse answer(User user, UUID sessionId, SubmitAnswerRequest request) {
		ReviewSession session = requireActiveSession(user, sessionId);
		String answer = request.answer() == null ? "" : request.answer().trim();
		ReviewTurn userTurn = reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.ANSWER, answer));
		if (isExplicitNoAnswer(answer)) {
			finishWithEvaluation(session, noAnswerEvaluation(user, session, "用户表示不清楚：" + answer), userTurn);
			return toResponse(session);
		}
		UserSettings settings = settingsService.findOrDefault(user);
		List<ReviewTurn> turns = reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
		int followUpCount = followUpCount(turns);
		int maxFollowUps = maxFollowUpsFor(session);
		LlmResult result = llmClient.complete(settings, reviewSystemPrompt(), answerDecisionPrompt(session, answer, turns, followUpCount, maxFollowUps));
		AnswerDecision decision = parseAnswerDecision(result.content(), session, answer);
		FollowUpDecision followUpDecision = followUpDecision(session, decision, followUpCount, maxFollowUps, turns);
		if (followUpDecision.shouldFollowUp()) {
			saveWeaknessEvents(session, userTurn, followUpDecision.weakSignals());
			reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP, followUpDecision.question()));
		}
		else {
			finishWithEvaluation(session, evaluationFromDecision(decision, session, answer), userTurn);
		}
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse unknown(User user, UUID sessionId) {
		ReviewSession session = requireActiveSession(user, sessionId);
		ReviewTurn unknownTurn = reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.UNKNOWN, "不会"));
		finishWithEvaluation(session, noAnswerEvaluation(user, session, "用户标记不会。"), unknownTurn);
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse clarify(User user, UUID sessionId, ClarifyRequest request) {
		ReviewSession session = requireActiveSession(user, sessionId);
		String question = request.question() == null || request.question().isBlank() ? "请解释题意。" : request.question().trim();
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.CLARIFICATION, question));
		UserSettings settings = settingsService.findOrDefault(user);
		LlmResult result = llmClient.complete(settings, clarifySystemPrompt(), clarifyPrompt(session, question));
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.CLARIFICATION,
				result.content() == null || result.content().isBlank()
						? fallbackClarification(session)
						: result.content().trim()));
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse skip(User user, UUID sessionId) {
		ReviewSession session = requireActiveSession(user, sessionId);
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.SKIP, "跳过本题"));
		Instant now = Instant.now(clock);
		session.abandon(now);
		todayReviewActionRepository.save(new TodayReviewAction(
				session.getUser(),
				session.getReviewUnit(),
				LocalDate.now(clock),
				TodayReviewActionType.DISMISS_TODAY,
				null));
		return toResponse(session);
	}

	private void finishWithEvaluation(ReviewSession session, ReviewEvaluation evaluation, ReviewTurn weaknessTurn) {
		Instant now = Instant.now(clock);
		session.evaluate(evaluation, now);
		ReviewPoint point = session.getReviewUnit();
		saveWeaknessEvents(session, weaknessTurn, evaluation.weakSignals());
		ReviewPointStatus status = toStatus(evaluation.nextStatus());
		Instant nextReviewAt = nextReviewAt(status, now);
		List<String> weakPoints = weakPointLabels(evaluation);
		point.updateReviewProgress(
				evaluation.score().overall(),
				status,
				now,
				nextReviewAt,
				point.getReviewCount() + 1,
				point.getWrongCount() + (evaluation.score().overall().compareTo(BigDecimal.valueOf(3)) < 0 ? 1 : 0),
				weakPoints,
				evaluation.nextProbe());
		if (evaluation.masteryCard() != null) {
			point.updateMasteryCard(evaluation.masteryCard());
		}
		ReviewAttemptResult result = attemptResult(evaluation.score().overall());
		session.getReviewUnitState().recordAttempt(result, now, nextReviewAt);
		reviewAttemptRepository.save(new ReviewAttempt(
				session.getUser(),
				point,
				session,
				session.getQuestionVariant(),
				ReviewAttemptSource.REVIEW_SESSION,
				result,
				evaluation.score().overall(),
				now,
				evaluation.overallComment()));
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.EVALUATION,
				evaluation.overallComment()));
	}

	private UserReviewUnitState requireReviewUnitState(User user, UUID stateId) {
		return reviewUnitStateRepository.findByIdAndUserIdWithUnit(stateId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review unit state not found."));
	}

	private ReviewSession requireSession(User user, UUID sessionId) {
		return reviewSessionRepository.findByIdAndUserIdWithUnit(sessionId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review session not found."));
	}

	private ReviewSession requireActiveSession(User user, UUID sessionId) {
		ReviewSession session = requireSession(user, sessionId);
		if (session.getStatus() != ReviewSessionStatus.ACTIVE) {
			throw new IllegalStateException("Review session is not active.");
		}
		return session;
	}

	private ReviewSessionResponse toResponse(ReviewSession session) {
		ReviewPoint point = session.getReviewUnit();
		List<ReviewTurn> turns = reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
		ReviewEvaluation evaluation = evaluationForResponse(session, turns);
		BigDecimal finalScore = evaluation == null || evaluation.score() == null
				? session.getFinalScore()
				: evaluation.score().overall();
		String summary = evaluation == null ? session.getSummary() : evaluation.overallComment();
		return new ReviewSessionResponse(
				session.getId(),
				session.getReviewUnitState().getId(),
				point.getId(),
				session.getQuestionVariant() == null ? null : session.getQuestionVariant().getId(),
				session.getQuestionVariant() == null ? null : session.getQuestionVariant().getTitle(),
				session.getStatus().name().toLowerCase(Locale.ROOT),
				point.getTopic().getTitle(),
				point.getTitle(),
				session.getStartedAt(),
				session.getEndedAt(),
				finalScore,
				summary,
				evaluation,
				point.getNextReviewAt(),
				reviewPlanExplanation(point),
				turns.stream()
						.map(turn -> new ReviewTurnResponse(
								turn.getId(),
								turn.getRole().name().toLowerCase(Locale.ROOT),
								turn.getTurnType().name().toLowerCase(Locale.ROOT),
								turn.getContent(),
								turn.getCreatedAt()))
						.toList());
	}

	private ReviewPlanExplanation reviewPlanExplanation(ReviewPoint point) {
		if (point == null) {
			return null;
		}
		LocalDate today = Instant.now(clock).atZone(clock.getZone()).toLocalDate();
		var priority = reviewPriorityService.explainReviewPoint(point, today, false);
		return new ReviewPlanExplanation(
				reviewIntervalLabel(point.getStatus()),
				reviewIntervalReason(point.getStatus()),
				point.getNextReviewAt() == null
						? null
						: point.getNextReviewAt().atZone(clock.getZone()).toLocalDate().toString(),
				priority.totalScore(),
				priority.factors()
						.stream()
						.map(factor -> new ReviewPlanFactor(
								factor.key(),
								factor.label(),
								factor.value(),
								factor.contribution(),
								factor.description()))
						.toList());
	}

	private ReviewEvaluation evaluationForResponse(ReviewSession session, List<ReviewTurn> turns) {
		ReviewEvaluation evaluation = session.getEvaluation();
		if (evaluation == null) {
			return null;
		}
		String evidence = noAnswerEvidence(turns);
		if (evidence == null) {
			return normalizeEvaluation(evaluation, session, latestUserAnswer(turns));
		}
		return normalizeNoAnswerEvaluation(evaluation, session, evidence, turns);
	}

	private AnswerDecision parseAnswerDecision(String content, ReviewSession session, String answer) {
		if (content != null && !content.isBlank()) {
			try {
				return objectMapper.readValue(extractJson(content), AnswerDecision.class);
			}
			catch (JsonProcessingException | RuntimeException ignored) {
				try {
					ReviewEvaluation evaluation = objectMapper.readValue(extractJson(content), ReviewEvaluation.class);
					return new AnswerDecision("evaluate", null, evaluation, safeWeakSignals(evaluation));
				}
				catch (JsonProcessingException | RuntimeException ignoredAgain) {
					// Fall through to deterministic local decision.
				}
			}
		}
		WeaknessSignal signal = new WeaknessSignal(
				"insufficient_evidence",
				"回答尚未形成可验证证据",
				excerpt(answer),
				4);
		return new AnswerDecision("follow_up", fallbackFollowUpQuestion(session), null, List.of(signal));
	}

	private ReviewEvaluation evaluationFromDecision(AnswerDecision decision, ReviewSession session, String answer) {
		ReviewEvaluation evaluation = decision.evaluation();
		if (evaluation == null || evaluation.score() == null || evaluation.score().overall() == null) {
			evaluation = localEvaluation(session, answer, false);
		}
		evaluation = normalizeEvaluation(evaluation, session, answer);
		List<WeaknessSignal> decisionSignals = safeList(decision.weakSignals());
		if (!decisionSignals.isEmpty()
				&& ("follow_up".equalsIgnoreCase(decision.action())
						|| evaluation.weakSignals() == null
						|| evaluation.weakSignals().isEmpty())) {
			ReviewScore score = normalizeScore(evaluation.score(), localScore(answer));
			List<String> missingPoints = safeList(evaluation.missingPoints());
			List<String> inaccuratePoints = safeList(evaluation.inaccuratePoints());
			return new ReviewEvaluation(
					evaluation.overallComment(),
					safeList(evaluation.correctPoints()),
					missingPoints,
					inaccuratePoints,
					teachingCorrections(
							evaluation,
							null,
							session,
							evaluation.referenceAnswer(),
							score,
							missingPoints,
							inaccuratePoints,
							decisionSignals),
					evaluation.referenceAnswer(),
					score,
					decisionSignals,
					weakPointLabels(decisionSignals),
					evaluation.nextProbe(),
					evaluation.nextStatus(),
					evaluation.masteryCard());
		}
		return evaluation;
	}

	private ReviewEvaluation noAnswerEvaluation(User user, ReviewSession session, String evidence) {
		UserSettings settings = settingsService.findOrDefault(user);
		List<ReviewTurn> turns = reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
		LlmResult result = llmClient.complete(settings, noAnswerSystemPrompt(), noAnswerPrompt(session, evidence, turns));
		ReviewEvaluation evaluation = parseReviewEvaluation(result.content());
		return normalizeNoAnswerEvaluation(evaluation, session, evidence, turns);
	}

	private ReviewEvaluation parseReviewEvaluation(String content) {
		if (content != null && !content.isBlank()) {
			try {
				return objectMapper.readValue(extractJson(content), ReviewEvaluation.class);
			}
			catch (JsonProcessingException | RuntimeException ignored) {
				// Fall through to deterministic local no-answer evaluation.
			}
		}
		return null;
	}

	private ReviewEvaluation normalizeEvaluation(ReviewEvaluation evaluation, ReviewSession session, String answer) {
		BigDecimal fallbackScore = localScore(answer);
		List<WeaknessSignal> signals = safeWeakSignals(evaluation);
		MasteryCard masteryCard = normalizeMasteryCard(
				evaluation.masteryCard() == null ? localMasteryCard(session) : evaluation.masteryCard(),
				session);
		List<String> missingPoints = safeList(evaluation.missingPoints());
		List<String> inaccuratePoints = safeList(evaluation.inaccuratePoints());
		String referenceAnswer = blankToDefault(evaluation.referenceAnswer(), localReferenceAnswer(session));
		ReviewScore score = normalizeScore(evaluation.score(), fallbackScore);
		List<Correction> corrections = teachingCorrections(
				evaluation,
				null,
				session,
				referenceAnswer,
				score,
				missingPoints,
				inaccuratePoints,
				signals);
		return new ReviewEvaluation(
				blankToDefault(evaluation.overallComment(), "回答已收口，仍需持续复验关键机制和边界。"),
				safeList(evaluation.correctPoints()),
				missingPoints,
				inaccuratePoints,
				corrections,
				referenceAnswer,
				score,
				signals,
				evaluation.weakPoints() == null || evaluation.weakPoints().isEmpty()
						? weakPointLabels(signals)
						: evaluation.weakPoints(),
				normalizeNextProbe(evaluation.nextProbe(), masteryCard.nextProbe(), session),
				blankToDefault(evaluation.nextStatus(), fallbackScore.compareTo(BigDecimal.valueOf(3)) >= 0 ? "first_pass" : "unstable"),
				masteryCard);
	}

	private ReviewEvaluation normalizeNoAnswerEvaluation(ReviewEvaluation evaluation, ReviewSession session, String evidence, List<ReviewTurn> turns) {
		ReviewEvaluation fallback = localNoAnswerEvaluation(session, evidence, turns);
		if (evaluation == null) {
			evaluation = fallback;
		}
		List<WeaknessSignal> signals = isUsefulWeakSignals(evaluation.weakSignals())
				? evaluation.weakSignals()
				: List.of();
		if (signals.isEmpty()) {
			signals = fallback.weakSignals();
		}
		List<String> missingPoints = isUsefulNoAnswerItems(evaluation.missingPoints())
				? evaluation.missingPoints()
				: fallback.missingPoints();
		List<String> inaccuratePoints = safeList(evaluation.inaccuratePoints());
		MasteryCard masteryCard = normalizeMasteryCard(
				isUsefulMasteryCard(evaluation.masteryCard())
						? evaluation.masteryCard()
						: fallback.masteryCard(),
				session);
		ReviewScore score = normalizeLowScore(evaluation.score());
		String referenceAnswer = isUsefulNoAnswerText(evaluation.referenceAnswer()) ? evaluation.referenceAnswer() : fallback.referenceAnswer();
		List<Correction> corrections = teachingCorrections(
				evaluation,
				fallback,
				session,
				referenceAnswer,
				score,
				missingPoints,
				inaccuratePoints,
				signals);
		return new ReviewEvaluation(
				isUsefulNoAnswerText(evaluation.overallComment()) ? evaluation.overallComment() : fallback.overallComment(),
				List.of(),
				missingPoints,
				inaccuratePoints,
				corrections,
				referenceAnswer,
				score,
				signals,
				isUsefulNoAnswerItems(evaluation.weakPoints()) ? evaluation.weakPoints() : weakPointLabels(signals),
				normalizeNextProbe(
						isUsefulNoAnswerText(evaluation.nextProbe()) ? evaluation.nextProbe() : fallback.nextProbe(),
						masteryCard.nextProbe(),
						session),
				"unstable",
				masteryCard);
	}

	private static ReviewEvaluation localNoAnswerEvaluation(ReviewSession session, String evidence, List<ReviewTurn> turns) {
		String target = sessionTitle(session);
		String context = reviewContext(session, turns);
		if (isCacheConsistencyContext(target, context)) {
			return cacheConsistencyNoAnswerEvaluation(target, evidence, context);
		}
		return genericNoAnswerEvaluation(target, evidence, context);
	}

	private static ReviewEvaluation cacheConsistencyNoAnswerEvaluation(String target, String evidence, String context) {
		BigDecimal score = BigDecimal.valueOf(1.5);
		List<WeaknessSignal> signals = List.of(
				new WeaknessSignal(
						"unknown",
						"Redis/MySQL 双写时序窗口不会分析",
						excerpt(evidence),
						4),
				new WeaknessSignal(
						"missing_production",
						"缺少删除失败、旧值回写和监控定位方案",
						excerpt(context),
						4));
		MasteryCard card = new MasteryCard(
				"先更新 MySQL 再删除 Redis 时，真正要判断的是“旧缓存还能存活多久”和“谁会把旧值重新写回缓存”。",
				List.of(
						"按时间线拆：写 MySQL 前、提交后、删除 Redis 前后、并发读回源后回写缓存",
						"逐个判断窗口：删缓存失败、读线程查到旧库值后回写、读从库延迟导致旧值回填",
						"说明持续时间：成功删除通常是毫秒级窗口，删除失败或旧值回填会持续到 TTL、重试删除或下一次写入",
						"给控制手段：删除失败重试、延迟双删、短 TTL、版本号/更新时间校验、CDC 或消息补偿删除"),
				List.of(
						"先更新库再删缓存不能保证强一致，只是把常见窗口压短",
						"最危险的不是单次读到旧缓存，而是旧值被并发读回源后重新写进 Redis",
						"生产定位要看商品 id 的 MySQL 版本/更新时间、Redis 值和 TTL、缓存删除日志、回源重建日志"),
				"围绕商品价格更新的缓存删除失败、并发读旧值回填和旧值持续时间继续考察，可换成时序题或生产定位题。");
		List<String> missingPoints = List.of(
				"没有拆出先更新 MySQL、再删除 Redis 之间短暂读到旧缓存的窗口",
				"没有说明删除 Redis 失败时旧价格会一直留到 TTL、补偿删除或下一次更新",
				"没有说明缓存 miss 后并发读可能查到旧库值，并在写线程删缓存之后把旧值回填到 Redis",
				"没有给出按商品 id 对比 MySQL 版本、Redis 值/TTL、删除日志和回源重建日志的定位步骤");
		String referenceAnswer = "两分钟回答可以这样说：先更新 MySQL 再删除 Redis 是常见的失效缓存策略，但仍然有窗口。第一，MySQL 已提交但 Redis 还没删时，并发读可能命中旧价格，这个窗口通常持续到删除命令成功。第二，删除 Redis 失败时，旧价格会继续保留，直到 TTL 到期、重试删除成功或下一次写入触发删除。第三，如果缓存本来 miss，读线程可能在写事务提交前查到旧库值，写线程提交后删除缓存，读线程再把旧值写回 Redis，这个旧值会持续到 TTL 或补偿删除。生产上要给缓存设置合理 TTL，删除失败进入重试或消息补偿，必要时做延迟双删；定位时按商品 id 查 MySQL 更新时间/版本、Redis 当前值和 TTL、删除缓存日志、回源重建日志和读写 trace。";
		List<Correction> corrections = List.of(
				new Correction(
						"没有说明删除 Redis 失败时旧缓存会持续多久",
						"删除 Redis 失败时，旧价格会继续留在 Redis，直到 TTL 到期、重试或补偿删除成功，或者下一次写入再次触发删除。",
						"这个点决定旧值窗口的持续时间，不能只说“会不一致”。"),
				new Correction(
						"没有说明缓存 miss 后旧值可能被并发读回填",
						"读线程可能在写事务提交前查到旧库值；写线程提交并删除缓存后，读线程再把旧值写入 Redis，旧值会持续到 TTL 或补偿删除。",
						"这比短暂命中旧缓存更危险，因为旧值会被重新种进缓存。"),
				new Correction(
						"没有拆出 MySQL 已提交但 Redis 还没删除的短窗口",
						"MySQL 提交后、Redis 删除成功前，并发读可能直接命中旧缓存；如果删除成功，这个窗口通常只持续到删除命令完成。",
						"这能区分毫秒级短窗口和删除失败导致的长窗口。"),
				new Correction(
						"没有给出生产定位证据",
						"按商品 id 对比 MySQL 更新时间或版本、Redis 当前值和 TTL、缓存删除日志、回源重建日志，以及读写 trace。",
						"定位必须落到可观测证据，否则无法判断旧值来自未删除缓存还是旧值回填。"));
		return new ReviewEvaluation(
				"这次没有形成有效作答。对这题应先补“写库、删缓存、并发读回源、旧值回写”的时序窗口，而不是只记“双写不一致”这个名词。",
				List.of(),
				missingPoints,
				List.of(),
				corrections,
				referenceAnswer,
				new ReviewScore(score, score, score, score, score),
				signals,
				weakPointLabels(signals),
				"围绕商品价格更新中的删除失败、并发读旧值回填、旧值持续时间和定位证据继续考察。",
				"unstable",
				card);
	}

	private static ReviewEvaluation genericNoAnswerEvaluation(String target, String evidence, String context) {
		BigDecimal score = BigDecimal.valueOf(1.5);
		String focus = excerpt(blankToDefault(context, target));
		List<WeaknessSignal> signals = List.of(new WeaknessSignal(
				"unknown",
				target + " 的题干场景没有展开",
				excerpt(evidence),
				4));
		List<String> missingPoints = List.of(
				"没有复述题干中的核心对象、操作顺序和状态变化：" + focus,
				"没有说明哪一步成功、哪一步失败时会产生不一致或结论翻转",
				"没有给出可用于面试复述的定位证据：日志、状态值、配置或 trace");
		String referenceAnswer = "两分钟回答建议：先用一句话说明「" + target + "」在题干场景里考什么；再按题干里的操作顺序逐步说明状态如何变化；然后列出前一步成功后一步失败、并发读写穿插、异步重试延迟等会让结论翻转的边界；最后给出生产定位入口，包括请求 trace、关键状态值、日志、配置和补偿任务。";
		MasteryCard card = new MasteryCard(
				"先把题干场景里的对象、操作顺序、状态变化和失败点说清，再判断「" + target + "」。",
				List.of(
						"复述题干中的核心对象和操作顺序：" + focus,
						"说明每一步会改变什么状态，以及哪个状态会被后续步骤读取",
						"列出至少两个失败点：前一步成功后一步失败、并发读写穿插、异步/重试延迟",
						"给出定位顺序：请求 trace、关键状态值、日志、配置和补偿任务"),
				List.of(
						"不要只说「" + target + "」这个标题，要把题干里的状态变化讲出来",
						"边界不是补充项，边界决定这题能不能判定掌握",
						"排查步骤必须能落到具体日志、状态值或配置项"),
				"围绕题干场景中的成功路径、失败路径和可观测证据继续考察，可用原题追问或改成场景判断题。");
		return new ReviewEvaluation(
				"这次没有形成有效作答。先不要背标题，应该把题干里的对象、操作顺序、状态变化和失败窗口拆出来。",
				List.of(),
				missingPoints,
				List.of(),
				fallbackCorrections(null, target, referenceAnswer, missingPoints, List.of(), signals),
				referenceAnswer,
				new ReviewScore(score, score, score, score, score),
				signals,
				weakPointLabels(signals),
				"围绕题干场景中的成功路径、失败路径和定位证据继续考察。",
				"unstable",
				card);
	}

	private ReviewEvaluation localEvaluation(ReviewSession session, String answer, boolean unknown) {
		BigDecimal overall = unknown ? BigDecimal.valueOf(1.5) : localScore(answer);
		String target = sessionTitle(session);
		List<WeaknessSignal> signals = List.of(new WeaknessSignal(
				unknown ? "unknown" : "insufficient_evidence",
				target + " 的机制边界仍需复验",
				excerpt(answer),
				overall.compareTo(BigDecimal.valueOf(3)) >= 0 ? 2 : 4));
		if (unknown) {
			return new ReviewEvaluation(
					"本题没有形成有效作答，只能判定为未掌握；需要先补齐核心链路，再做追问复验。",
					List.of(),
					List.of(
							"没有说出 " + target + " 的核心执行链路",
							"没有说明关键边界、失败条件或反例",
							"没有形成可用于面试复述的排查步骤"),
					List.of(),
					"先用一句话定义「" + target + "」，再按核心流程、关键分支、失效边界、排查入口组织两分钟回答。",
					new ReviewScore(overall, overall, overall, overall, overall),
					signals,
					weakPointLabels(signals),
					"围绕 " + target + " 的核心链路、关键边界和排查步骤继续考察，可用原问题追问或换成边界/场景题。",
					"unstable",
					localMasteryCard(session));
		}
		return new ReviewEvaluation(
				overall.compareTo(BigDecimal.valueOf(3)) >= 0
						? "回答覆盖了主线，但还需要继续补边界和生产排查证据。"
						: "当前回答证据不足，需要重点复习机制链路和边界条件。",
				List.of("能围绕 " + target + " 展开表达"),
				List.of("需要补充关键调用链路、异常边界和生产排查闭环"),
				List.of(),
				"两分钟回答建议：先给结论，再按核心机制、边界场景、常见失效原因、排查路径和工程取舍展开。",
				new ReviewScore(overall, overall, overall, overall, overall),
				signals,
				weakPointLabels(signals),
				"围绕 " + target + " 的核心链路、失效边界和排查步骤继续考察，可换成机制解释题或生产排查题。",
				overall.compareTo(BigDecimal.valueOf(3)) >= 0 ? "first_pass" : "unstable",
				localMasteryCard(session));
	}

	private static FollowUpDecision followUpDecision(
			ReviewSession session,
			AnswerDecision decision,
			int followUpCount,
			int maxFollowUps,
			List<ReviewTurn> turns) {
		if (followUpCount >= maxFollowUps) {
			return FollowUpDecision.none();
		}
		List<WeaknessSignal> signals = decisionWeakSignals(decision);
		if ("follow_up".equalsIgnoreCase(decision.action())) {
			String question = blankToDefault(decision.followUpQuestion(), fallbackFollowUpQuestion(session)).trim();
			return isRepeatedFollowUp(question, turns)
					? FollowUpDecision.none()
					: new FollowUpDecision(true, question, signals);
		}
		if (!hasBlockingGap(decision)) {
			return FollowUpDecision.none();
		}
		String question = forcedFollowUpQuestion(session, decision);
		return isRepeatedFollowUp(question, turns)
				? FollowUpDecision.none()
				: new FollowUpDecision(true, question, signals);
	}

	private static int maxFollowUpsFor(ReviewSession session) {
		ReviewPoint point = session.getReviewUnit();
		if (point.getDifficulty() >= 5 && (point.getImportance() >= 5 || point.getInterviewFrequency() >= 5)) {
			return HARD_MAX_FOLLOW_UPS;
		}
		if (point.getImportance() >= 4
				|| point.getInterviewFrequency() >= 4
				|| point.getMastery().compareTo(LOW_MASTERY_THRESHOLD) < 0) {
			return CORE_MAX_FOLLOW_UPS;
		}
		return DEFAULT_MAX_FOLLOW_UPS;
	}

	private static boolean hasBlockingGap(AnswerDecision decision) {
		if (hasHighSeveritySignal(decisionWeakSignals(decision))) {
			return true;
		}
		ReviewEvaluation evaluation = decision.evaluation();
		return evaluation != null
				&& (!safeList(evaluation.missingPoints()).isEmpty()
						|| !safeList(evaluation.inaccuratePoints()).isEmpty());
	}

	private static boolean hasHighSeveritySignal(List<WeaknessSignal> signals) {
		return safeList(signals).stream()
				.anyMatch(signal -> signal != null && signal.severity() >= BLOCKING_WEAKNESS_SEVERITY);
	}

	private static List<WeaknessSignal> decisionWeakSignals(AnswerDecision decision) {
		List<WeaknessSignal> directSignals = safeList(decision.weakSignals());
		if (!directSignals.isEmpty()) {
			return directSignals;
		}
		ReviewEvaluation evaluation = decision.evaluation();
		List<WeaknessSignal> evaluationSignals = safeWeakSignals(evaluation);
		if (!evaluationSignals.isEmpty()) {
			return evaluationSignals;
		}
		if (evaluation != null && !safeList(evaluation.missingPoints()).isEmpty()) {
			return evaluation.missingPoints().stream()
					.filter(point -> point != null && !point.isBlank())
					.map(point -> new WeaknessSignal("missing_evidence", point.trim(), null, BLOCKING_WEAKNESS_SEVERITY))
					.toList();
		}
		return List.of();
	}

	private static String forcedFollowUpQuestion(ReviewSession session, AnswerDecision decision) {
		String target = sessionTitle(session);
		List<WeaknessSignal> signals = decisionWeakSignals(decision);
		String gap = signals.stream()
				.filter(signal -> signal != null && signal.label() != null && !signal.label().isBlank())
				.map(signal -> signal.label().trim())
				.findFirst()
				.orElseGet(() -> firstMissingPoint(decision));
		if (gap == null || gap.isBlank()) {
			return fallbackFollowUpQuestion(session);
		}
		return "这里还不能直接收口，因为「" + gap + "」没有验证清楚。请只补充「" + target + "」中这个缺口对应的机制链路、边界或生产排查证据。";
	}

	private static String firstMissingPoint(AnswerDecision decision) {
		ReviewEvaluation evaluation = decision.evaluation();
		if (evaluation == null) {
			return "";
		}
		return safeList(evaluation.missingPoints()).stream()
				.filter(point -> point != null && !point.isBlank())
				.findFirst()
				.orElse("");
	}

	private static String fallbackFollowUpQuestion(ReviewSession session) {
		return "你的回答还不足以收口。请只补充「" + sessionTitle(session) + "」的一个关键缺口：调用链路、失效边界或生产排查证据中你最确定的一项。";
	}

	private static String fallbackClarification(ReviewSession session) {
		return "这道题主要考察你对「" + sessionTitle(session) + "」的机制、边界和排查路径的理解。回答时先给结论，再补关键链路、失效场景和排查入口。";
	}

	private static boolean isExplicitNoAnswer(String answer) {
		if (answer == null) {
			return true;
		}
		String normalized = answer.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
		if (normalized.isBlank()) {
			return true;
		}
		List<String> noAnswerSignals = List.of(
				"不会",
				"不清楚",
				"不知道",
				"不懂",
				"没思路",
				"没有思路",
				"想不起来",
				"答不上来",
				"看不懂",
				"不太清楚");
		return noAnswerSignals.stream().anyMatch(normalized::contains) && normalized.length() <= 12;
	}

	private static String noAnswerEvidence(List<ReviewTurn> turns) {
		if (turns == null || turns.isEmpty()) {
			return null;
		}
		for (int i = turns.size() - 1; i >= 0; i--) {
			ReviewTurn turn = turns.get(i);
			if (turn == null || turn.getRole() != ReviewTurnRole.USER) {
				continue;
			}
			if (turn.getTurnType() == ReviewTurnType.UNKNOWN) {
				return "用户标记不会。";
			}
			if (turn.getTurnType() == ReviewTurnType.ANSWER && isExplicitNoAnswer(turn.getContent())) {
				return "用户表示不清楚：" + blankToDefault(turn.getContent(), "");
			}
		}
		return null;
	}

	private static boolean isRepeatedFollowUp(String candidate, List<ReviewTurn> turns) {
		String normalizedCandidate = normalizeForComparison(candidate);
		if (normalizedCandidate.length() < 12 || turns == null || turns.isEmpty()) {
			return false;
		}
		return turns.stream()
				.filter(turn -> turn.getRole() == ReviewTurnRole.AI && turn.getTurnType() == ReviewTurnType.FOLLOW_UP)
				.map(turn -> normalizeForComparison(turn.getContent()))
				.anyMatch(previous -> !previous.isBlank()
						&& (previous.equals(normalizedCandidate)
								|| previous.contains(normalizedCandidate)
								|| normalizedCandidate.contains(previous)));
	}

	private static String normalizeForComparison(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
	}

	private void saveWeaknessEvents(ReviewSession session, ReviewTurn turn, List<WeaknessSignal> signals) {
		ReviewPoint point = session.getReviewUnit();
		if (signals == null || signals.isEmpty()) {
			return;
		}
		List<ReviewWeaknessEvent> events = signals.stream()
				.filter(signal -> signal != null && signal.label() != null && !signal.label().isBlank())
				.map(signal -> new ReviewWeaknessEvent(
						point,
						session,
						turn,
						blankToDefault(signal.category(), "unknown"),
						signal.label().trim(),
						signal.evidence(),
						signal.severity()))
				.toList();
		if (!events.isEmpty()) {
			weaknessEventRepository.saveAll(events);
		}
	}

	private static int followUpCount(List<ReviewTurn> turns) {
		return (int) turns.stream().filter(turn -> turn.getTurnType() == ReviewTurnType.FOLLOW_UP).count();
	}

	private static List<WeaknessSignal> safeWeakSignals(ReviewEvaluation evaluation) {
		return evaluation == null ? List.of() : safeList(evaluation.weakSignals());
	}

	private static List<String> weakPointLabels(ReviewEvaluation evaluation) {
		if (evaluation.weakPoints() != null && !evaluation.weakPoints().isEmpty()) {
			return evaluation.weakPoints();
		}
		return weakPointLabels(evaluation.weakSignals());
	}

	private static List<String> weakPointLabels(List<WeaknessSignal> signals) {
		return safeList(signals).stream()
				.map(WeaknessSignal::label)
				.filter(label -> label != null && !label.isBlank())
				.distinct()
				.limit(5)
				.toList();
	}

	private static List<Correction> teachingCorrections(
			ReviewEvaluation evaluation,
			ReviewEvaluation fallback,
			ReviewSession session,
			String referenceAnswer,
			ReviewScore score,
			List<String> missingPoints,
			List<String> inaccuratePoints,
			List<WeaknessSignal> signals) {
		List<Correction> primary = cleanCorrections(evaluation == null ? null : evaluation.corrections());
		if (!needsTeaching(score, missingPoints, inaccuratePoints, signals)) {
			return primary.stream().limit(6).toList();
		}
		int expectedCount = expectedCorrectionCount(missingPoints, inaccuratePoints, signals);
		List<Correction> generatedItems = fallbackCorrections(
				session,
				session == null ? "" : sessionTitle(session),
				referenceAnswer,
				missingPoints,
				inaccuratePoints,
				signals);
		List<Correction> fallbackItems = mergeCorrections(
				generatedItems,
				cleanCorrections(fallback == null ? null : fallback.corrections()),
				expectedCount);
		return mergeCorrections(primary, fallbackItems, expectedCount);
	}

	private static boolean needsTeaching(
			ReviewScore score,
			List<String> missingPoints,
			List<String> inaccuratePoints,
			List<WeaknessSignal> signals) {
		return score == null
				|| score.overall() == null
				|| score.overall().compareTo(NEAR_PERFECT_SCORE_THRESHOLD) < 0
				|| !compactStrings(missingPoints).isEmpty()
				|| !compactStrings(inaccuratePoints).isEmpty()
				|| hasHighSeveritySignal(signals);
	}

	private static int expectedCorrectionCount(
			List<String> missingPoints,
			List<String> inaccuratePoints,
			List<WeaknessSignal> signals) {
		List<String> issues = correctionIssues(missingPoints, inaccuratePoints, signals);
		return Math.max(1, Math.min(6, issues.size()));
	}

	private static List<Correction> mergeCorrections(
			List<Correction> primary,
			List<Correction> fallback,
			int expectedCount) {
		LinkedHashMap<String, Correction> merged = new LinkedHashMap<>();
		for (Correction correction : safeList(primary)) {
			merged.putIfAbsent(normalizeForComparison(correction.userIssue()), correction);
		}
		if (merged.size() < expectedCount) {
			for (Correction correction : safeList(fallback)) {
				merged.putIfAbsent(normalizeForComparison(correction.userIssue()), correction);
				if (merged.size() >= expectedCount) {
					break;
				}
			}
		}
		if (merged.isEmpty()) {
			return safeList(fallback).stream().limit(1).toList();
		}
		return merged.values().stream().limit(6).toList();
	}

	private static List<Correction> cleanCorrections(List<Correction> corrections) {
		LinkedHashMap<String, Correction> cleaned = new LinkedHashMap<>();
		for (Correction correction : safeList(corrections)) {
			if (correction == null || !hasConcreteText(correction.userIssue()) || !isUsefulCorrectionAnswer(correction.correctAnswer())) {
				continue;
			}
			String userIssue = correction.userIssue().trim();
			cleaned.putIfAbsent(
					normalizeForComparison(userIssue),
					new Correction(
							userIssue,
							correction.correctAnswer().trim(),
							blankToDefault(correction.explanation(), "这个点会影响本题是否真正掌握；下次回答要把正确结论和触发条件说全。").trim()));
		}
		return cleaned.values().stream().limit(6).toList();
	}

	private static boolean isUsefulCorrectionAnswer(String value) {
		if (!hasConcreteText(value)) {
			return false;
		}
		String normalized = normalizeForComparison(value);
		List<String> genericFragments = List.of(
				"参考答案",
				"referenceanswer",
				"见上",
				"见下",
				"见完整答法",
				"详见",
				"参考referenceanswer");
		return genericFragments.stream().noneMatch(normalized::contains);
	}

	private static List<Correction> fallbackCorrections(
			ReviewSession session,
			String target,
			String referenceAnswer,
			List<String> missingPoints,
			List<String> inaccuratePoints,
			List<WeaknessSignal> signals) {
		String resolvedTarget = blankToDefault(target, session == null ? "当前复习点" : sessionTitle(session));
		String resolvedReferenceAnswer = blankToDefault(
				referenceAnswer,
				"正确回答要围绕「" + resolvedTarget + "」先给结论，再说明核心机制、关键边界和生产排查证据。");
		return correctionIssues(missingPoints, inaccuratePoints, signals).stream()
				.limit(6)
				.map(issue -> new Correction(
						issue,
						correctAnswerForIssue(resolvedTarget, issue, resolvedReferenceAnswer),
						explanationForIssue(resolvedTarget, issue)))
				.toList();
	}

	private static List<String> correctionIssues(
			List<String> missingPoints,
			List<String> inaccuratePoints,
			List<WeaknessSignal> signals) {
		List<String> problemItems = compactStrings(missingPoints);
		List<String> inaccurateItems = compactStrings(inaccuratePoints).stream()
				.map(item -> "不准确：" + item)
				.toList();
		List<String> directIssues = compactStrings(joinLists(problemItems, inaccurateItems));
		if (!directIssues.isEmpty()) {
			return directIssues;
		}
		List<String> signalIssues = safeList(signals).stream()
				.filter(signal -> signal != null)
				.map(WeaknessSignal::label)
				.filter(label -> label != null && !label.isBlank())
				.distinct()
				.limit(6)
				.toList();
		if (!signalIssues.isEmpty()) {
			return signalIssues;
		}
		return List.of("这次回答没有充分证明机制、边界和生产排查证据都已掌握");
	}

	private static List<String> compactStrings(List<String> values) {
		LinkedHashMap<String, String> compacted = new LinkedHashMap<>();
		for (String value : safeList(values)) {
			if (value == null || value.isBlank()) {
				continue;
			}
			String trimmed = value.trim();
			compacted.putIfAbsent(normalizeForComparison(trimmed), trimmed);
		}
		return compacted.values().stream().toList();
	}

	private static List<String> joinLists(List<String> left, List<String> right) {
		return java.util.stream.Stream.concat(safeList(left).stream(), safeList(right).stream()).toList();
	}

	private static String correctAnswerForIssue(String target, String issue, String referenceAnswer) {
		String normalized = normalizeForComparison(target + " " + issue);
		boolean cacheContext = normalized.contains("redis")
				|| normalized.contains("mysql")
				|| normalized.contains("缓存")
				|| normalized.contains("双写");
		if (cacheContext && normalized.contains("删除") && normalized.contains("失败")) {
			return "删除 Redis 失败时，旧值会继续留在缓存里，直到 TTL 到期、重试或补偿删除成功，或者下一次写入再次触发删除。";
		}
		if (cacheContext && (normalized.contains("旧值回填") || normalized.contains("回填") || normalized.contains("miss"))) {
			return "缓存 miss 的读线程可能在写事务提交前查到旧库值；写线程提交并删除缓存后，这个读线程再把旧值写回 Redis，旧值会持续到 TTL 或补偿删除。";
		}
		if (cacheContext && normalized.contains("ttl")) {
			return "旧值持续时间要按场景拆：删除成功前通常是短窗口；删除失败或旧值回填后，会持续到 TTL 到期、重试或补偿删除成功。";
		}
		if (cacheContext && (normalized.contains("定位") || normalized.contains("日志") || normalized.contains("trace"))) {
			return "生产定位要按 key 或商品 id 对比 MySQL 更新时间/版本、Redis 当前值和 TTL、缓存删除日志、回源重建日志，以及读写 trace。";
		}
		if (cacheContext && (normalized.contains("先更新mysql") || normalized.contains("删除redis"))) {
			return "先更新 MySQL 再删除 Redis 仍有窗口：MySQL 提交后、Redis 删除成功前，并发读可能命中旧缓存；删除成功后这个短窗口才结束。";
		}

		boolean transactionContext = normalized.contains("事务")
				|| normalized.contains("transaction")
				|| normalized.contains("代理")
				|| normalized.contains("spring");
		if (transactionContext && (normalized.contains("selfinvocation") || normalized.contains("自调用"))) {
			return "self-invocation 是同一个目标对象内部方法调用，不会重新经过 Spring 代理对象，因此事务拦截器不会执行，方法上的 @Transactional 可能不生效。";
		}
		if (transactionContext && normalized.contains("代理")) {
			return "Spring 事务是否生效首先看调用有没有进入 Spring 生成的代理对象；只有经过代理，TransactionInterceptor 才能开启、提交或回滚事务。";
		}
		if (transactionContext && (normalized.contains("异常") || normalized.contains("回滚"))) {
			return "事务回滚还取决于异常类型和 rollbackFor 配置；默认主要回滚 RuntimeException/Error，受检异常需要显式配置。";
		}
		if (transactionContext && (normalized.contains("private") || normalized.contains("final"))) {
			return "private/final 方法、不可被代理增强的方法，或者没有被 Spring 容器代理的调用入口，都可能让 @Transactional 失效。";
		}
		if (transactionContext && (normalized.contains("定位") || normalized.contains("排查") || normalized.contains("日志"))) {
			return "生产排查先看调用入口拿到的是不是代理对象，再看事务日志、异常类型、传播行为配置，以及是否存在 self-invocation。";
		}

		return referenceAnswer;
	}

	private static String explanationForIssue(String target, String issue) {
		String normalized = normalizeForComparison(target + " " + issue);
		if (normalized.contains("redis") || normalized.contains("mysql") || normalized.contains("缓存") || normalized.contains("双写")) {
			return "这个点决定旧值窗口从“短暂不一致”变成“持续脏缓存”，必须讲清触发条件和持续到什么时候。";
		}
		if (normalized.contains("事务") || normalized.contains("transaction") || normalized.contains("代理") || normalized.contains("spring")) {
			return "这个点决定 @Transactional 是否真的经过拦截器；只背注解名不能证明掌握事务生效边界。";
		}
		return "这个点会影响本题是否真正掌握；面试回答时要先给正确结论，再补机制、边界和可观测证据。";
	}

	private static <T> List<T> safeList(List<T> values) {
		return values == null ? List.of() : values;
	}

	private static ReviewScore normalizeScore(ReviewScore score, BigDecimal fallback) {
		if (score == null) {
			return new ReviewScore(fallback, fallback, fallback, fallback, fallback);
		}
		return new ReviewScore(
				score.conclusionAccuracy() == null ? fallback : score.conclusionAccuracy(),
				score.mechanismExplanation() == null ? fallback : score.mechanismExplanation(),
				score.boundaryCases() == null ? fallback : score.boundaryCases(),
				score.transferApplication() == null ? fallback : score.transferApplication(),
				score.overall() == null ? fallback : score.overall());
	}

	private static ReviewScore normalizeLowScore(ReviewScore score) {
		BigDecimal fallback = BigDecimal.valueOf(1.5);
		if (score == null) {
			return new ReviewScore(fallback, fallback, fallback, fallback, fallback);
		}
		return new ReviewScore(
				capLow(score.conclusionAccuracy(), fallback),
				capLow(score.mechanismExplanation(), fallback),
				capLow(score.boundaryCases(), fallback),
				capLow(score.transferApplication(), fallback),
				capLow(score.overall(), fallback));
	}

	private static BigDecimal capLow(BigDecimal value, BigDecimal fallback) {
		BigDecimal normalized = value == null ? fallback : value;
		if (normalized.compareTo(BigDecimal.ZERO) < 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal max = BigDecimal.valueOf(2);
		return normalized.compareTo(max) > 0 ? max : normalized;
	}

	private static boolean isUsefulMasteryCard(MasteryCard masteryCard) {
		if (masteryCard == null
				|| masteryCard.oneSentence() == null
				|| masteryCard.oneSentence().isBlank()
				|| safeList(masteryCard.answerSkeleton()).isEmpty()
				|| safeList(masteryCard.mustRemember()).isEmpty()
				|| masteryCard.nextProbe() == null
				|| masteryCard.nextProbe().isBlank()) {
			return false;
		}
		String combined = (masteryCard.oneSentence()
				+ " "
				+ String.join(" ", safeList(masteryCard.answerSkeleton()))
				+ " "
				+ String.join(" ", safeList(masteryCard.mustRemember()))
				+ " "
				+ masteryCard.nextProbe()).toLowerCase(Locale.ROOT);
		List<String> genericFragments = List.of(
				"四段式表达",
				"先给结论",
				"再讲关键调用链路",
				"补充常见失效边界",
				"最后落到生产排查",
				"结论 机制 边界 排查",
				"结论机制边界排查");
		if (genericFragments.stream().anyMatch(combined::contains)) {
			return false;
		}
		long concreteItems = safeList(masteryCard.answerSkeleton()).stream()
				.filter(ReviewSessionService::hasConcreteText)
				.count()
				+ safeList(masteryCard.mustRemember()).stream()
						.filter(ReviewSessionService::hasConcreteText)
						.count();
		return hasConcreteText(masteryCard.oneSentence())
				&& hasConcreteText(masteryCard.nextProbe())
				&& concreteItems >= 2;
	}

	private static MasteryCard normalizeMasteryCard(MasteryCard masteryCard, ReviewSession session) {
		MasteryCard fallback = localMasteryCard(session);
		MasteryCard source = masteryCard == null ? fallback : masteryCard;
		List<String> answerSkeleton = compactStrings(source.answerSkeleton());
		List<String> mustRemember = compactStrings(source.mustRemember());
		return new MasteryCard(
				blankToDefault(source.oneSentence(), fallback.oneSentence()),
				answerSkeleton.isEmpty() ? fallback.answerSkeleton() : answerSkeleton,
				mustRemember.isEmpty() ? fallback.mustRemember() : mustRemember,
				normalizeNextProbe(source.nextProbe(), fallback.nextProbe(), session));
	}

	private static String normalizeNextProbe(String value, String fallback, ReviewSession session) {
		String resolved = blankToDefault(value, fallback).trim();
		if (resolved.isBlank()) {
			return localMasteryCard(session).nextProbe();
		}
		if (!looksLikeFixedQuestion(resolved)) {
			return resolved;
		}
		String direction = stripQuestionLead(resolved);
		if (direction.isBlank()) {
			direction = sessionTitle(session);
		}
		return "围绕" + direction + "继续考察，可用原问题，也可换成场景题、对比题或生产排查题。";
	}

	private static boolean looksLikeFixedQuestion(String value) {
		String normalized = value.trim();
		return normalized.endsWith("？")
				|| normalized.endsWith("?")
				|| normalized.startsWith("请")
				|| normalized.startsWith("为什么")
				|| normalized.startsWith("如何")
				|| normalized.startsWith("怎么")
				|| normalized.startsWith("说明")
				|| normalized.startsWith("要求");
	}

	private static String stripQuestionLead(String value) {
		String normalized = value.trim()
				.replaceFirst("^[请要求]+", "")
				.replaceFirst("^独立", "")
				.replaceFirst("^(为什么|如何|怎么|说明)", "")
				.replaceAll("[？?。.]$", "")
				.trim();
		return normalized.startsWith("围绕") ? normalized.substring("围绕".length()).trim() : normalized;
	}

	private static boolean isUsefulNoAnswerItems(List<String> values) {
		if (safeList(values).isEmpty()) {
			return false;
		}
		return safeList(values).stream()
				.filter(ReviewSessionService::isUsefulNoAnswerText)
				.count() >= Math.min(2, values.size());
	}

	private static boolean isUsefulWeakSignals(List<WeaknessSignal> signals) {
		if (safeList(signals).isEmpty()) {
			return false;
		}
		return safeList(signals).stream()
				.anyMatch(signal -> signal != null
						&& signal.severity() >= BLOCKING_WEAKNESS_SEVERITY
						&& isUsefulNoAnswerText(signal.label()));
	}

	private static boolean isUsefulNoAnswerText(String value) {
		if (!hasConcreteText(value)) {
			return false;
		}
		String normalized = normalizeForComparison(value);
		List<String> genericFragments = List.of(
				"核心链路",
				"核心执行链路",
				"关键调用链路",
				"执行链路",
				"机制边界",
				"关键边界",
				"异常边界",
				"失败条件",
				"排查步骤",
				"排查入口",
				"核心流程",
				"没有形成有效作答",
				"先补齐",
				"两分钟回答建议",
				"结论机制边界排查");
		long genericHits = genericFragments.stream().filter(normalized::contains).count();
		if (genericHits >= 1) {
			return false;
		}
		return normalized.length() >= 12;
	}

	private static boolean hasConcreteText(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String normalized = value.trim();
		return normalized.length() >= 8 && !List.of("结论", "机制", "边界", "排查", "回答骨架", "必须记住").contains(normalized);
	}

	private static String reviewContext(ReviewSession session, List<ReviewTurn> turns) {
		String question = latestQuestion(turns);
		String target = sessionTitle(session);
		if (question.isBlank()) {
			return target;
		}
		return target + "。题干：" + question;
	}

	private static String latestQuestion(List<ReviewTurn> turns) {
		if (turns == null || turns.isEmpty()) {
			return "";
		}
		for (int i = turns.size() - 1; i >= 0; i--) {
			ReviewTurn turn = turns.get(i);
			if (turn != null && turn.getTurnType() == ReviewTurnType.QUESTION && turn.getContent() != null) {
				return turn.getContent().trim();
			}
		}
		return "";
	}

	private static String latestUserAnswer(List<ReviewTurn> turns) {
		if (turns == null || turns.isEmpty()) {
			return "";
		}
		for (int i = turns.size() - 1; i >= 0; i--) {
			ReviewTurn turn = turns.get(i);
			if (turn != null && turn.getRole() == ReviewTurnRole.USER && turn.getContent() != null) {
				return turn.getContent().trim();
			}
		}
		return "";
	}

	private static boolean isCacheConsistencyContext(String target, String context) {
		String normalized = normalizeForComparison(target + " " + context);
		return (normalized.contains("redis") || normalized.contains("缓存"))
				&& (normalized.contains("mysql") || normalized.contains("数据库"))
				&& (normalized.contains("双写") || normalized.contains("一致") || normalized.contains("删除缓存") || normalized.contains("删缓存"));
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String excerpt(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= 160) {
			return normalized;
		}
		return normalized.substring(0, 160) + "...";
	}

	private static MasteryCard localMasteryCard(ReviewSession session) {
		String target = sessionTitle(session);
		return new MasteryCard(
				"能独立说明「" + target + "」的触发入口、执行链路、失效条件和定位入口，才算可收口。",
				List.of(
						"一句话界定「" + target + "」在什么条件下成立或失效",
						"按入口、关键组件、调用路径或数据变化说清主链路",
						"列出至少两个会让结论翻转的边界条件",
						"给出从现象到日志、配置或源码入口的定位顺序"),
				List.of(
						"不要只背「" + target + "」的概念名，必须讲清触发条件",
						"边界条件要能解释为什么结论会翻转",
						"排查表达要落到可观察现象、配置检查或调用入口"),
				"围绕 " + target + " 的核心链路、失效边界和排查步骤继续考察，可换成机制解释题、边界判断题或生产排查题。");
	}

	private static String localReferenceAnswer(ReviewSession session) {
		return "两分钟回答建议：围绕「" + sessionTitle(session) + "」先给结论，再按核心机制、边界场景、生产排查和工程取舍展开。";
	}

	private static BigDecimal localScore(String answer) {
		int length = answer == null ? 0 : answer.trim().length();
		double score = Math.min(4.2, 2.0 + length / 120.0);
		return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
	}

	private static ReviewPointStatus toStatus(String value) {
		if (value == null) {
			return ReviewPointStatus.UNSTABLE;
		}
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "first_pass" -> ReviewPointStatus.FIRST_PASS;
			case "due" -> ReviewPointStatus.DUE;
			case "stable" -> ReviewPointStatus.STABLE;
			case "long_term" -> ReviewPointStatus.LONG_TERM;
			default -> ReviewPointStatus.UNSTABLE;
		};
	}

	private static ReviewAttemptResult attemptResult(BigDecimal score) {
		BigDecimal normalized = score == null ? BigDecimal.ZERO : score;
		if (normalized.compareTo(BigDecimal.valueOf(3)) < 0) {
			return ReviewAttemptResult.POOR;
		}
		if (normalized.compareTo(BigDecimal.valueOf(4.2)) < 0) {
			return ReviewAttemptResult.PARTIAL;
		}
		return ReviewAttemptResult.GOOD;
	}

	private static Instant nextReviewAt(ReviewPointStatus status, Instant now) {
		return switch (status) {
			case UNSTABLE -> now.plus(1, ChronoUnit.DAYS);
			case FIRST_PASS -> now.plus(3, ChronoUnit.DAYS);
			case DUE -> now.plus(7, ChronoUnit.DAYS);
			case STABLE -> now.plus(14, ChronoUnit.DAYS);
			case LONG_TERM -> now.plus(30, ChronoUnit.DAYS);
			case UNCOVERED -> now.plus(1, ChronoUnit.DAYS);
		};
	}

	private static String reviewIntervalLabel(ReviewPointStatus status) {
		return switch (status) {
			case UNSTABLE -> "明天复习";
			case FIRST_PASS -> "3 天后复习";
			case DUE -> "7 天后复习";
			case STABLE -> "14 天后复习";
			case LONG_TERM -> "30 天后复习";
			case UNCOVERED -> "明天复习";
		};
	}

	private static String reviewIntervalReason(ReviewPointStatus status) {
		return switch (status) {
			case UNSTABLE -> "本题暴露关键薄弱点或掌握不稳定，需要短间隔复验。";
			case FIRST_PASS -> "本题已初步掌握，但还没有稳定，需要 3 天后确认能否保持。";
			case DUE -> "本题仍需按期复验，暂按 7 天间隔进入后续计划。";
			case STABLE -> "本题掌握稳定，进入 14 天巩固复习。";
			case LONG_TERM -> "本题已接近长期掌握，进入 30 天长期复习。";
			case UNCOVERED -> "本题仍按未覆盖处理，明天优先复验。";
		};
	}

	private String generateInitialQuestion(User user, ReviewPoint point, QuestionVariant variant) {
		UserSettings settings = settingsService.findOrDefault(user);
		LlmResult result = llmClient.complete(settings, questionSystemPrompt(), questionPrompt(point, variant));
		if (result.content() != null && !result.content().isBlank()) {
			return result.content().trim();
		}
		if (variant != null && variant.getPrompt() != null && !variant.getPrompt().isBlank()) {
			return variant.getPrompt().trim();
		}
		throw new IllegalStateException("AI 题目生成失败：" + (result.errorMessage() == null ? "LLM 未返回有效题目。" : result.errorMessage()));
	}

	private static String sessionTitle(ReviewSession session) {
		return session.getReviewUnit().getTitle();
	}

	private static String reviewSystemPrompt() {
		return "你是严格的高级 Java 面试官。必须只输出 JSON，不要输出 Markdown。根据用户回答判断继续追问还是收口评价。";
	}

	private static String noAnswerSystemPrompt() {
		return "你是严格的高级 Java 面试复习教练。用户明确不会作答时，必须只输出 ReviewEvaluation JSON，不要输出 Markdown。你的目标是给出可执行补缺复盘，而不是安慰或泛泛评分。";
	}

	private static String questionSystemPrompt() {
		return "你是严格的高级 Java 面试官。根据复习单元和题目变体生成一道题，只输出题目本身，不输出参考答案、评分标准或解释。题目必须具体、可回答、能暴露机制理解和生产经验。";
	}

	private static String questionPrompt(ReviewPoint point, QuestionVariant variant) {
		String base = "主题：" + point.getTopic().getTitle()
				+ "\n复习点：" + point.getTitle()
				+ "\n重要度：" + point.getImportance()
				+ "\n难度：" + point.getDifficulty()
				+ "\n面试频率：" + point.getInterviewFrequency()
				+ "\n当前掌握分：" + point.getMastery()
				+ "\n当前状态：" + point.getStatus()
				+ "\n历史弱点：" + (point.getWeakPoints().isEmpty() ? "暂无" : String.join("；", point.getWeakPoints()))
				+ "\n下次考察方向：" + (point.getNextProbe() == null || point.getNextProbe().isBlank() ? "暂无" : point.getNextProbe());
		if (variant == null) {
			return base + "\n请生成一道针对这个复习点的实时面试题。";
		}
		return base
				+ "\n题目变体：" + variant.getTitle()
				+ "\n变体类型：" + variant.getVariantType()
				+ "\n变体难度：" + variant.getDifficulty()
				+ "\n变体焦点：" + (variant.getFocus() == null || variant.getFocus().isBlank() ? "暂无" : variant.getFocus())
				+ "\n变体原题：" + variant.getPrompt()
				+ "\n请优先围绕这个题目变体出题。可以轻微改写题干，但不能换掉考察目标。";
	}

	private static String clarifySystemPrompt() {
		return "你是高级 Java 面试题意澄清助手。只解释题目意图、考察维度和回答结构，不泄露标准答案，不替用户作答。";
	}

	private static String answerDecisionPrompt(
			ReviewSession session,
			String answer,
			List<ReviewTurn> turns,
			int followUpCount,
			int maxFollowUps) {
		return """
				请根据完整对话判断用户对当前复习点的掌握程度。

				复习点：%s
				题目变体：%s
				已追问次数：%d
				最多追问次数：%d
				历史对话：
				%s

				本轮用户回答：
				%s

				输出 JSON，字段如下：
				{
				  "action": "follow_up 或 evaluate",
				  "followUpQuestion": "当 action=follow_up 时给出一个针对缺失证据的追问；否则为 null。追问不能泄露完整正确答案",
				  "weakSignals": [
				    {"category": "missing_mechanism|missing_boundary|missing_production|concept_confusion|expression_gap|other", "label": "薄弱点短句", "evidence": "引用用户回答里的证据", "severity": 1到5}
				  ],
				  "evaluation": {
				    "overallComment": "本题判定：一句话说明掌握程度、答对了什么、主要错漏是什么",
				    "correctPoints": ["答对的点"],
				    "missingPoints": ["遗漏点"],
				    "inaccuratePoints": ["不准确点"],
				    "corrections": [
				      {"userIssue": "用户具体答错或漏掉了什么", "correctAnswer": "对应的正确说法，必须能直接教会用户", "explanation": "为什么这个点影响本题判断"}
				    ],
				    "referenceAnswer": "追问完成后的简洁两分钟参考回答；要能直接教会用户正确答法",
				    "score": {"conclusionAccuracy": 0到5, "mechanismExplanation": 0到5, "boundaryCases": 0到5, "transferApplication": 0到5, "overall": 0到5},
				    "weakSignals": [
				      {"category": "missing_mechanism", "label": "薄弱点短句", "evidence": "证据", "severity": 1到5}
				    ],
				    "weakPoints": ["薄弱点短句"],
				    "nextProbe": "下次考察方向：只描述要继续验证的机制、边界或场景类型，不要写成固定题目",
				    "nextStatus": "unstable|first_pass|due|stable|long_term",
				    "masteryCard": {
				      "oneSentence": "复习记录的一句话结论，不重复 referenceAnswer",
				      "answerSkeleton": ["可复述的回答骨架，保留关键步骤，不重复纠错明细"],
				      "mustRemember": ["必须记住的薄弱点或边界事实"],
				      "nextProbe": "下次考察方向：只描述验证方向，可用原问题，也可换成场景题、对比题或排查题"
				    }
				  }
				}

				判定规则：
				1. 如果关键机制、边界场景或生产排查证据缺失，且未达到最多追问次数，action 必须为 follow_up。
				2. followUpQuestion 只能问一个具体缺口，不能泄露参考答案。
				3. 如果可以收口或已达到最多追问次数，action 必须为 evaluate，并填写 evaluation；此时才给 corrections 和 referenceAnswer。
				4. 不要因为用户回答字数长就判定掌握，必须看机制、边界和生产经验。
				5. 如果用户回答“不清楚、不会、不知道、没思路”等明确无法作答，不要继续追问，action 必须为 evaluate，给低分、薄弱点和 masteryCard。
				6. 不要重复历史对话中已经问过的追问；如果没有新的具体缺口可以问，action 必须为 evaluate。
				7. 当 action=evaluate 且 overall < 4.8，或 missingPoints/inaccuratePoints/weakSignals 任一非空，corrections 必须非空。
				8. corrections 必须逐条对应用户的错误点或遗漏点：userIssue 写用户哪里错/漏，correctAnswer 直接给正确说法，explanation 解释为什么这样才对。除非用户接近完美且没有缺口，否则不能只给 referenceAnswer。
				9. 最终展示会压成三块：本题判定、纠错与正确答案、复习记录。不要在 overallComment、corrections、referenceAnswer、masteryCard 中反复输出同一句话。
				10. nextProbe 和 masteryCard.nextProbe 必须是“考察方向”，不能是固定下次追问题目，不能以“请/为什么/如何/说明”开头。
				""".formatted(
						sessionTitle(session),
						sessionVariantContext(session),
						followUpCount,
						maxFollowUps,
						transcript(turns),
				answer);
	}

	private static String noAnswerPrompt(ReviewSession session, String evidence, List<ReviewTurn> turns) {
		return """
				用户在当前复习点明确表示不会作答。请直接生成一次低分收口复盘，用于前端展示“本题判定、纠错与正确答案、复习记录”。

				复习点：%s
				题目变体：%s
				用户证据：%s
				历史对话：
				%s

				只输出下面这个 ReviewEvaluation JSON，不要包在其他字段里：
				{
				  "overallComment": "本题判定：没有形成有效作答，指出应先补哪条主链路",
				  "correctPoints": [],
				  "missingPoints": ["具体缺口 1", "具体缺口 2", "具体缺口 3"],
				  "inaccuratePoints": [],
				  "corrections": [
				    {"userIssue": "用户没有回答出的具体缺口", "correctAnswer": "这个缺口对应的正确说法", "explanation": "为什么必须这样回答"}
				  ],
				  "referenceAnswer": "追问结束后的两分钟参考回答，必须具体到机制、边界和排查入口",
				  "score": {"conclusionAccuracy": 0到2, "mechanismExplanation": 0到2, "boundaryCases": 0到2, "transferApplication": 0到2, "overall": 0到2},
				  "weakSignals": [
				    {"category": "unknown|missing_mechanism|missing_boundary|missing_production", "label": "具体薄弱点", "evidence": "必须引用用户证据", "severity": 4到5}
				  ],
				  "weakPoints": ["具体薄弱点"],
				  "nextProbe": "下次考察方向：只描述要继续验证的机制、边界或场景类型，不要写成固定题目",
				  "nextStatus": "unstable",
				  "masteryCard": {
				    "oneSentence": "复习记录的一句话结论：说明这个复习点真正要掌握的判断条件或核心链路",
				    "answerSkeleton": ["具体回答步骤，不要只写结论/机制/边界/排查", "具体回答步骤", "具体回答步骤"],
				    "mustRemember": ["必须记住的机制事实或边界事实", "必须记住的排查入口或反例"],
				    "nextProbe": "与 nextProbe 一致的下次考察方向，可用原题、场景题、对比题或排查题验证"
				  }
				}

				生成要求：
				1. correctPoints 必须是空数组，因为用户没有形成有效作答。
				2. missingPoints 必须写“没有说出什么机制/边界/排查入口”，不能写“回答不完整”这种空话。
				3. masteryCard 不能使用“结论、机制、边界、排查”作为单独条目，必须写成可背诵、可复述、可考察的具体内容。
				4. weakSignals 的 evidence 必须包含用户证据原文。
				5. 所有分数必须在 0 到 2 之间，nextStatus 必须是 unstable。
				6. corrections 必须非空，每个条目都要把用户没答出的缺口和正确说法配对，不能只说“参考 referenceAnswer”。
				7. nextProbe 和 masteryCard.nextProbe 必须是考察方向，不是固定下次追问题目，不能以“请/为什么/如何/说明”开头。
				""".formatted(sessionTitle(session), sessionVariantContext(session), evidence, transcript(turns));
	}

	private static String clarifyPrompt(ReviewSession session, String question) {
		return """
				复习点：%s
				题目变体：%s
				用户的题意追问：%s

				请用 3 到 5 行解释：
				1. 这题实际在问什么。
				2. 面试官想看哪些维度。
				3. 推荐回答顺序。

				不要给标准答案，不要展开具体结论。
				""".formatted(sessionTitle(session), sessionVariantContext(session), question);
	}

	private static String sessionVariantContext(ReviewSession session) {
		QuestionVariant variant = session.getQuestionVariant();
		if (variant == null) {
			return "无固定变体，按复习点实时生成";
		}
		return variant.getTitle()
				+ "；焦点：" + (variant.getFocus() == null || variant.getFocus().isBlank() ? "暂无" : variant.getFocus())
				+ "；原题：" + variant.getPrompt();
	}

	private static String transcript(List<ReviewTurn> turns) {
		if (turns == null || turns.isEmpty()) {
			return "暂无";
		}
		return turns.stream()
				.skip(Math.max(0, turns.size() - 12))
				.map(turn -> turn.getRole().name().toLowerCase(Locale.ROOT)
						+ "/"
						+ turn.getTurnType().name().toLowerCase(Locale.ROOT)
						+ "："
						+ turn.getContent())
				.reduce((left, right) -> left + "\n" + right)
				.orElse("暂无");
	}

	private static String extractJson(String value) {
		int start = value.indexOf('{');
		int end = value.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return value.substring(start, end + 1);
		}
		return value;
	}

	private record AnswerDecision(
			String action,
			String followUpQuestion,
			ReviewEvaluation evaluation,
			List<WeaknessSignal> weakSignals) {
	}

	private record FollowUpDecision(
			boolean shouldFollowUp,
			String question,
			List<WeaknessSignal> weakSignals) {

		private static FollowUpDecision none() {
			return new FollowUpDecision(false, "", List.of());
		}
	}
}

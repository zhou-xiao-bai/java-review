package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.ReviewTask;
import com.javareview.today.ReviewTaskRepository;
import com.javareview.today.ReviewTaskStatus;
import com.javareview.reviewsession.ReviewEvaluation.ReviewScore;
import com.javareview.reviewsession.ReviewEvaluation.WeaknessSignal;
import com.javareview.reviewsession.ReviewSessionDtos.ClarifyRequest;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewSessionResponse;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewTurnResponse;
import com.javareview.reviewsession.ReviewSessionDtos.StartReviewSessionRequest;
import com.javareview.reviewsession.ReviewSessionDtos.SubmitAnswerRequest;

@Service
public class ReviewSessionService {

	private static final int MAX_FOLLOW_UPS = 3;

	private final ReviewTaskRepository reviewTaskRepository;
	private final ReviewSessionRepository reviewSessionRepository;
	private final ReviewTurnRepository reviewTurnRepository;
	private final ReviewWeaknessEventRepository weaknessEventRepository;
	private final SettingsService settingsService;
	private final LlmClient llmClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ReviewSessionService(
			ReviewTaskRepository reviewTaskRepository,
			ReviewSessionRepository reviewSessionRepository,
			ReviewTurnRepository reviewTurnRepository,
			ReviewWeaknessEventRepository weaknessEventRepository,
			SettingsService settingsService,
			LlmClient llmClient,
			ObjectMapper objectMapper,
			Clock clock) {
		this.reviewTaskRepository = reviewTaskRepository;
		this.reviewSessionRepository = reviewSessionRepository;
		this.reviewTurnRepository = reviewTurnRepository;
		this.weaknessEventRepository = weaknessEventRepository;
		this.settingsService = settingsService;
		this.llmClient = llmClient;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public ReviewSessionResponse start(User user, StartReviewSessionRequest request) {
		ReviewTask task = requireTask(user, request.taskId());
		if (task.isRemoved()) {
			throw new IllegalStateException("Review task has been removed from today's plan.");
		}
		if (task.getStatus() == ReviewTaskStatus.COMPLETED || task.getStatus() == ReviewTaskStatus.SKIPPED) {
			throw new IllegalStateException("Review task is no longer startable.");
		}
		if (task.getStatus() == ReviewTaskStatus.IN_PROGRESS) {
			List<ReviewSession> activeSessions = reviewSessionRepository.findActiveByTaskIdAndUserId(task.getId(), user.getId());
			if (!activeSessions.isEmpty()) {
				return toResponse(activeSessions.getFirst());
			}
		}
		task.start();
		ReviewSession session = reviewSessionRepository.save(new ReviewSession(user, task, Instant.now(clock)));
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.QUESTION, generateInitialQuestion(user, task)));
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
			finishWithEvaluation(session, localEvaluation(session, "用户表示不清楚：" + answer, true), userTurn);
			return toResponse(session);
		}
		UserSettings settings = settingsService.findOrDefault(user);
		List<ReviewTurn> turns = reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
		int followUpCount = followUpCount(turns);
		LlmResult result = llmClient.complete(settings, reviewSystemPrompt(), answerDecisionPrompt(session, answer, turns, followUpCount));
		AnswerDecision decision = parseAnswerDecision(result.content(), session, answer);
		if (shouldContinueWithFollowUp(decision, followUpCount, turns)) {
			saveWeaknessEvents(session, userTurn, decision.weakSignals());
			reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP,
					decision.followUpQuestion() == null || decision.followUpQuestion().isBlank()
							? fallbackFollowUpQuestion(session)
							: decision.followUpQuestion().trim()));
		}
		else {
			finishWithEvaluation(session, evaluationFromDecision(decision, session, answer), userTurn);
		}
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse unknown(User user, UUID sessionId) {
		ReviewSession session = requireActiveSession(user, sessionId);
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.UNKNOWN, "不会"));
		finishWithEvaluation(session, localEvaluation(session, "用户标记不会。", true), null);
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
		session.getTask().skip(Instant.now(clock));
		session.abandon(Instant.now(clock));
		return toResponse(session);
	}

	private void finishWithEvaluation(ReviewSession session, ReviewEvaluation evaluation, ReviewTurn weaknessTurn) {
		Instant now = Instant.now(clock);
		session.evaluate(evaluation, now);
		session.getTask().complete(now);
		ReviewPoint point = session.getTask().getReviewPoint();
		if (point != null) {
			saveWeaknessEvents(session, weaknessTurn, evaluation.weakSignals());
			ReviewPointStatus status = toStatus(evaluation.nextStatus());
			List<String> weakPoints = weakPointLabels(evaluation);
			point.updateReviewProgress(
					evaluation.score().overall(),
					status,
					now,
					nextReviewAt(status, now),
					point.getReviewCount() + 1,
					point.getWrongCount() + (evaluation.score().overall().compareTo(BigDecimal.valueOf(3)) < 0 ? 1 : 0),
					weakPoints,
					evaluation.nextProbe());
			if (evaluation.masteryCard() != null) {
				point.updateMasteryCard(evaluation.masteryCard());
			}
		}
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.EVALUATION,
				evaluation.overallComment()));
	}

	private ReviewTask requireTask(User user, UUID taskId) {
		return reviewTaskRepository.findByIdAndUserIdWithPoint(taskId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Review task not found."));
	}

	private ReviewSession requireSession(User user, UUID sessionId) {
		return reviewSessionRepository.findByIdAndUserIdWithTask(sessionId, user.getId())
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
		ReviewTask task = session.getTask();
		ReviewPoint point = task.getReviewPoint();
		return new ReviewSessionResponse(
				session.getId(),
				task.getId(),
				session.getStatus().name().toLowerCase(Locale.ROOT),
				point == null ? null : point.getTopic().getTitle(),
				point == null ? null : point.getTitle(),
				task.getManualPrompt(),
				session.getStartedAt(),
				session.getEndedAt(),
				session.getFinalScore(),
				session.getSummary(),
				session.getEvaluation(),
				reviewTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
						.map(turn -> new ReviewTurnResponse(
								turn.getId(),
								turn.getRole().name().toLowerCase(Locale.ROOT),
								turn.getTurnType().name().toLowerCase(Locale.ROOT),
								turn.getContent(),
								turn.getCreatedAt()))
						.toList());
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
		if (answer != null && answer.trim().length() < 80) {
			WeaknessSignal signal = new WeaknessSignal(
					"insufficient_evidence",
					"回答证据不足，缺少机制链路和边界场景",
					answer,
					4);
			return new AnswerDecision("follow_up", fallbackFollowUpQuestion(session), null, List.of(signal));
		}
		ReviewEvaluation evaluation = localEvaluation(session, answer, false);
		return new AnswerDecision("evaluate", null, evaluation, safeWeakSignals(evaluation));
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
			return new ReviewEvaluation(
					evaluation.overallComment(),
					safeList(evaluation.correctPoints()),
					safeList(evaluation.missingPoints()),
					safeList(evaluation.inaccuratePoints()),
					evaluation.referenceAnswer(),
					normalizeScore(evaluation.score(), localScore(answer)),
					decisionSignals,
					weakPointLabels(decisionSignals),
					evaluation.nextProbe(),
					evaluation.nextStatus(),
					evaluation.masteryCard());
		}
		return evaluation;
	}

	private ReviewEvaluation normalizeEvaluation(ReviewEvaluation evaluation, ReviewSession session, String answer) {
		BigDecimal fallbackScore = localScore(answer);
		List<WeaknessSignal> signals = safeWeakSignals(evaluation);
		MasteryCard masteryCard = evaluation.masteryCard() == null ? localMasteryCard(session) : evaluation.masteryCard();
		return new ReviewEvaluation(
				blankToDefault(evaluation.overallComment(), "回答已收口，仍需持续复验关键机制和边界。"),
				safeList(evaluation.correctPoints()),
				safeList(evaluation.missingPoints()),
				safeList(evaluation.inaccuratePoints()),
				blankToDefault(evaluation.referenceAnswer(), localReferenceAnswer(session)),
				normalizeScore(evaluation.score(), fallbackScore),
				signals,
				evaluation.weakPoints() == null || evaluation.weakPoints().isEmpty()
						? weakPointLabels(signals)
						: evaluation.weakPoints(),
				blankToDefault(evaluation.nextProbe(), masteryCard.nextProbe()),
				blankToDefault(evaluation.nextStatus(), fallbackScore.compareTo(BigDecimal.valueOf(3)) >= 0 ? "first_pass" : "unstable"),
				masteryCard);
	}

	private ReviewEvaluation localEvaluation(ReviewSession session, String answer, boolean unknown) {
		BigDecimal overall = unknown ? BigDecimal.valueOf(1.5) : localScore(answer);
		String target = taskTitle(session.getTask());
		List<WeaknessSignal> signals = List.of(new WeaknessSignal(
				unknown ? "unknown" : "insufficient_evidence",
				target + " 的机制边界仍需复验",
				answer,
				overall.compareTo(BigDecimal.valueOf(3)) >= 0 ? 2 : 4));
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
				"要求说明 " + target + " 的核心链路、失效边界和排查步骤。",
				overall.compareTo(BigDecimal.valueOf(3)) >= 0 ? "first_pass" : "unstable",
				localMasteryCard(session));
	}

	private static boolean shouldContinueWithFollowUp(AnswerDecision decision, int followUpCount, List<ReviewTurn> turns) {
		return "follow_up".equalsIgnoreCase(decision.action())
				&& followUpCount < MAX_FOLLOW_UPS
				&& !isRepeatedFollowUp(decision.followUpQuestion(), turns);
	}

	private static String fallbackFollowUpQuestion(ReviewSession session) {
		return "你刚才的回答还比较短。请补充「" + taskTitle(session.getTask()) + "」在生产问题中的排查路径，以及最容易被忽略的边界。";
	}

	private static String fallbackClarification(ReviewSession session) {
		return "这道题主要考察你对「" + taskTitle(session.getTask()) + "」的机制、边界和排查路径的理解。回答时先给结论，再补关键链路、失效场景和排查入口。";
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
		ReviewPoint point = session.getTask().getReviewPoint();
		if (point == null || signals == null || signals.isEmpty()) {
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

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static MasteryCard localMasteryCard(ReviewSession session) {
		String target = taskTitle(session.getTask());
		return new MasteryCard(
				target + " 需要用结论、机制、边界和排查路径四段式表达。",
				List.of(
						"先给结论，说明核心机制是什么",
						"再讲关键调用链路或数据流",
						"补充常见失效边界和反例",
						"最后落到生产排查入口和工程取舍"),
				List.of(
						"不要只背概念，要讲清触发条件",
						"边界场景通常比定义更能拉开差距",
						"回答需要能落到生产问题排查"),
				"要求说明 " + target + " 的核心链路、失效边界和排查步骤。");
	}

	private static String localReferenceAnswer(ReviewSession session) {
		return "两分钟回答建议：围绕「" + taskTitle(session.getTask()) + "」先给结论，再按核心机制、边界场景、生产排查和工程取舍展开。";
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

	private String generateInitialQuestion(User user, ReviewTask task) {
		UserSettings settings = settingsService.findOrDefault(user);
		LlmResult result = llmClient.complete(settings, questionSystemPrompt(), questionPrompt(task));
		if (result.content() != null && !result.content().isBlank()) {
			return result.content().trim();
		}
		throw new IllegalStateException("AI 题目生成失败：" + (result.errorMessage() == null ? "LLM 未返回有效题目。" : result.errorMessage()));
	}

	private static String taskTitle(ReviewTask task) {
		return task.getReviewPoint() == null ? task.getManualPrompt() : task.getReviewPoint().getTitle();
	}

	private static String reviewSystemPrompt() {
		return "你是严格的高级 Java 面试官。必须只输出 JSON，不要输出 Markdown。根据用户回答判断继续追问还是收口评分。";
	}

	private static String questionSystemPrompt() {
		return "你是严格的高级 Java 面试官。根据复习点实时生成一道题，只输出题目本身，不输出参考答案、评分标准或解释。题目必须具体、可回答、能暴露机制理解和生产经验。";
	}

	private static String questionPrompt(ReviewTask task) {
		ReviewPoint point = task.getReviewPoint();
		if (point == null) {
			return "手动复习任务：" + task.getManualPrompt() + "\n请生成一道严格面试复习题。";
		}
		return "主题：" + point.getTopic().getTitle()
				+ "\n复习点：" + point.getTitle()
				+ "\n重要度：" + point.getImportance()
				+ "\n难度：" + point.getDifficulty()
				+ "\n面试频率：" + point.getInterviewFrequency()
				+ "\n当前掌握分：" + point.getMastery()
				+ "\n当前状态：" + point.getStatus()
				+ "\n历史弱点：" + (point.getWeakPoints().isEmpty() ? "暂无" : String.join("；", point.getWeakPoints()))
				+ "\n下一次探针：" + (point.getNextProbe() == null || point.getNextProbe().isBlank() ? "暂无" : point.getNextProbe())
				+ "\n请生成一道针对这个复习点的实时面试题。";
	}

	private static String clarifySystemPrompt() {
		return "你是高级 Java 面试题意澄清助手。只解释题目意图、考察维度和回答结构，不泄露标准答案，不替用户作答。";
	}

	private static String answerDecisionPrompt(
			ReviewSession session,
			String answer,
			List<ReviewTurn> turns,
			int followUpCount) {
		return """
				请根据完整对话判断用户对当前复习点的掌握程度。

				复习点：%s
				已追问次数：%d
				最多追问次数：%d
				历史对话：
				%s

				本轮用户回答：
				%s

				输出 JSON，字段如下：
				{
				  "action": "follow_up 或 evaluate",
				  "followUpQuestion": "当 action=follow_up 时给出一个针对缺失证据的追问；否则为 null",
				  "weakSignals": [
				    {"category": "missing_mechanism|missing_boundary|missing_production|concept_confusion|expression_gap|other", "label": "薄弱点短句", "evidence": "引用用户回答里的证据", "severity": 1到5}
				  ],
				  "evaluation": {
				    "overallComment": "收口评价",
				    "correctPoints": ["答对的点"],
				    "missingPoints": ["遗漏点"],
				    "inaccuratePoints": ["不准确点"],
				    "referenceAnswer": "简洁的两分钟参考回答",
				    "score": {"conclusionAccuracy": 0到5, "mechanismExplanation": 0到5, "boundaryCases": 0到5, "transferApplication": 0到5, "overall": 0到5},
				    "weakSignals": [
				      {"category": "missing_mechanism", "label": "薄弱点短句", "evidence": "证据", "severity": 1到5}
				    ],
				    "weakPoints": ["薄弱点短句"],
				    "nextProbe": "下次复验要问的探针问题",
				    "nextStatus": "unstable|first_pass|due|stable|long_term",
				    "masteryCard": {
				      "oneSentence": "一句话掌握",
				      "answerSkeleton": ["回答骨架"],
				      "mustRemember": ["必须记住的关键点"],
				      "nextProbe": "下次复验探针"
				    }
				  }
				}

				判定规则：
				1. 如果关键机制、边界场景或生产排查证据缺失，且未达到最多追问次数，action 必须为 follow_up。
				2. followUpQuestion 只能问一个具体缺口，不能泄露参考答案。
				3. 如果可以收口或已达到最多追问次数，action 必须为 evaluate，并填写 evaluation。
				4. 不要因为用户回答字数长就判定掌握，必须看机制、边界和生产经验。
				5. 如果用户回答“不清楚、不会、不知道、没思路”等明确无法作答，不要继续追问，action 必须为 evaluate，给低分、薄弱点和 masteryCard。
				6. 不要重复历史对话中已经问过的追问；如果没有新的具体缺口可以问，action 必须为 evaluate。
				""".formatted(
						taskTitle(session.getTask()),
						followUpCount,
						MAX_FOLLOW_UPS,
						transcript(turns),
						answer);
	}

	private static String clarifyPrompt(ReviewSession session, String question) {
		return """
				复习点：%s
				用户的题意追问：%s

				请用 3 到 5 行解释：
				1. 这题实际在问什么。
				2. 面试官想看哪些维度。
				3. 推荐回答顺序。

				不要给标准答案，不要展开具体结论。
				""".formatted(taskTitle(session.getTask()), question);
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
}

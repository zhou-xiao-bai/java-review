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
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.ReviewTask;
import com.javareview.today.ReviewTaskRepository;
import com.javareview.reviewsession.ReviewEvaluation.ReviewScore;
import com.javareview.reviewsession.ReviewSessionDtos.ClarifyRequest;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewSessionResponse;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewTurnResponse;
import com.javareview.reviewsession.ReviewSessionDtos.StartReviewSessionRequest;
import com.javareview.reviewsession.ReviewSessionDtos.SubmitAnswerRequest;

@Service
public class ReviewSessionService {

	private final ReviewTaskRepository reviewTaskRepository;
	private final ReviewSessionRepository reviewSessionRepository;
	private final ReviewTurnRepository reviewTurnRepository;
	private final SettingsService settingsService;
	private final LlmClient llmClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public ReviewSessionService(
			ReviewTaskRepository reviewTaskRepository,
			ReviewSessionRepository reviewSessionRepository,
			ReviewTurnRepository reviewTurnRepository,
			SettingsService settingsService,
			LlmClient llmClient,
			ObjectMapper objectMapper,
			Clock clock) {
		this.reviewTaskRepository = reviewTaskRepository;
		this.reviewSessionRepository = reviewSessionRepository;
		this.reviewTurnRepository = reviewTurnRepository;
		this.settingsService = settingsService;
		this.llmClient = llmClient;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public ReviewSessionResponse start(User user, StartReviewSessionRequest request) {
		ReviewTask task = requireTask(user, request.taskId());
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
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.ANSWER, request.answer().trim()));
		UserSettings settings = settingsService.findOrDefault(user);
		LlmResult result = llmClient.complete(settings, reviewSystemPrompt(), answerPrompt(session, request.answer()));
		if (shouldFollowUp(request.answer(), result)) {
			reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.FOLLOW_UP,
					followUpQuestion(session, request.answer(), result)));
		}
		else {
			finishWithEvaluation(session, parseEvaluation(result.content(), session, request.answer(), false));
		}
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse unknown(User user, UUID sessionId) {
		ReviewSession session = requireActiveSession(user, sessionId);
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.UNKNOWN, "不会"));
		finishWithEvaluation(session, localEvaluation(session, "用户标记不会。", true));
		return toResponse(session);
	}

	@Transactional
	public ReviewSessionResponse clarify(User user, UUID sessionId, ClarifyRequest request) {
		ReviewSession session = requireActiveSession(user, sessionId);
		String question = request.question() == null || request.question().isBlank() ? "请解释题意。" : request.question().trim();
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.USER, ReviewTurnType.CLARIFICATION, question));
		reviewTurnRepository.save(new ReviewTurn(session, ReviewTurnRole.AI, ReviewTurnType.CLARIFICATION,
				"这道题主要考察你对「" + taskTitle(session.getTask()) + "」的机制、边界和排查路径的理解。请先讲结论，再补关键链路和容易失效的场景。"));
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

	private void finishWithEvaluation(ReviewSession session, ReviewEvaluation evaluation) {
		Instant now = Instant.now(clock);
		session.evaluate(evaluation, now);
		session.getTask().complete(now);
		ReviewPoint point = session.getTask().getReviewPoint();
		if (point != null) {
			ReviewPointStatus status = toStatus(evaluation.nextStatus());
			point.updateReviewProgress(
					evaluation.score().overall(),
					status,
					now,
					nextReviewAt(status, now),
					point.getReviewCount() + 1,
					point.getWrongCount() + (evaluation.score().overall().compareTo(BigDecimal.valueOf(3)) < 0 ? 1 : 0),
					evaluation.weakPoints(),
					evaluation.nextProbe());
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

	private ReviewEvaluation parseEvaluation(String content, ReviewSession session, String answer, boolean unknown) {
		if (content != null && !content.isBlank()) {
			try {
				return objectMapper.readValue(extractJson(content), ReviewEvaluation.class);
			}
			catch (JsonProcessingException | RuntimeException ignored) {
				// Fall through to deterministic local evaluation.
			}
		}
		return localEvaluation(session, answer, unknown);
	}

	private ReviewEvaluation localEvaluation(ReviewSession session, String answer, boolean unknown) {
		BigDecimal overall = unknown ? BigDecimal.valueOf(1.5) : localScore(answer);
		String target = taskTitle(session.getTask());
		return new ReviewEvaluation(
				overall.compareTo(BigDecimal.valueOf(3)) >= 0
						? "回答覆盖了主线，但还需要继续补边界和生产排查证据。"
						: "当前回答证据不足，需要重点复习机制链路和边界条件。",
				List.of("能围绕 " + target + " 展开表达"),
				List.of("需要补充关键调用链路、异常边界和生产排查闭环"),
				List.of(),
				"两分钟回答建议：先给结论，再按核心机制、边界场景、常见失效原因、排查路径和工程取舍展开。",
				new ReviewScore(overall, overall, overall, overall, overall),
				List.of(target + " 的机制边界仍需复验"),
				"要求说明 " + target + " 的核心链路、失效边界和排查步骤。",
				overall.compareTo(BigDecimal.valueOf(3)) >= 0 ? "first_pass" : "unstable");
	}

	private static boolean shouldFollowUp(String answer, LlmResult result) {
		return answer.trim().length() < 80 && (result.content() == null || !result.content().contains("\"overall"));
	}

	private static String followUpQuestion(ReviewSession session, String answer, LlmResult result) {
		if (result.content() != null && !result.content().isBlank() && result.content().length() < 500) {
			return result.content();
		}
		return "你刚才的回答还比较短。请补充「" + taskTitle(session.getTask()) + "」在生产问题中的排查路径，以及最容易被忽略的边界。";
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
		return "你是严格的高级 Java 面试官。只输出 JSON 或一个追问问题，不提前泄露参考答案。";
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

	private static String answerPrompt(ReviewSession session, String answer) {
		return "复习点：" + taskTitle(session.getTask()) + "\n用户回答：" + answer
				+ "\n如果证据不足，输出一个追问问题。若可以收口，输出符合 ReviewEvaluation 字段的 JSON。";
	}

	private static String extractJson(String value) {
		int start = value.indexOf('{');
		int end = value.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return value.substring(start, end + 1);
		}
		return value;
	}
}

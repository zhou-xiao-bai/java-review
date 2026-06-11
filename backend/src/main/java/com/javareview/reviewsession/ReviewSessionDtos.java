package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReviewSessionDtos {

	private ReviewSessionDtos() {
	}

	public record StartReviewSessionRequest(@NotNull UUID reviewUnitStateId) {
	}

	public record SubmitAnswerRequest(
			@NotBlank
			@Size(max = 8000)
			String answer) {
	}

	public record ClarifyRequest(
			@Size(max = 1000)
			String question) {
	}

	public record ReviewSessionResponse(
			UUID id,
			UUID reviewUnitStateId,
			UUID reviewUnitId,
			String status,
			String topicTitle,
			String pointTitle,
			Instant startedAt,
			Instant endedAt,
			BigDecimal finalScore,
			String summary,
			ReviewEvaluation evaluation,
			Instant nextReviewAt,
			ReviewPlanExplanation reviewPlanExplanation,
			List<ReviewTurnResponse> turns) {
	}

	public record ReviewPlanExplanation(
			String scheduleRule,
			String scheduleReason,
			String nextReviewAtText,
			BigDecimal priorityScore,
			List<ReviewPlanFactor> priorityFactors) {
	}

	public record ReviewPlanFactor(
			String key,
			String label,
			String value,
			BigDecimal contribution,
			String description) {
	}

	public record ReviewTurnResponse(
			UUID id,
			String role,
			String turnType,
			String content,
			Instant createdAt) {
	}
}

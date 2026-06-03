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

	public record StartReviewSessionRequest(@NotNull UUID taskId) {
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
			UUID taskId,
			String status,
			String topicTitle,
			String pointTitle,
			String manualPrompt,
			Instant startedAt,
			Instant endedAt,
			BigDecimal finalScore,
			String summary,
			ReviewEvaluation evaluation,
			List<ReviewTurnResponse> turns) {
	}

	public record ReviewTurnResponse(
			UUID id,
			String role,
			String turnType,
			String content,
			Instant createdAt) {
	}
}

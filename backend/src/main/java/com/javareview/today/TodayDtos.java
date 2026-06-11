package com.javareview.today;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.javareview.reviewunit.TodayReviewActionType;

public final class TodayDtos {

	private TodayDtos() {
	}

	public record TodayQueueResponse(
			LocalDate date,
			List<TodayQueueGroupResponse> groups) {
	}

	public record TodayActionRequest(
			@NotNull UUID reviewUnitStateId,
			@NotNull TodayReviewActionType actionType,
			LocalDate postponeUntil) {
	}

	public record TodayQueueGroupResponse(
			String reason,
			String label,
			long count,
			List<TodayQueueItemResponse> items) {
	}

	public record TodayQueueItemResponse(
			UUID reviewUnitId,
			UUID stateId,
			UUID scopeId,
			String scopeTitle,
			String domainName,
			String unitTitle,
			String status,
			String reason,
			String reasonLabel,
			int importance,
			int difficulty,
			int interviewFrequency,
			Instant nextReviewAt,
			Instant admittedAt,
			Instant lastReviewedAt,
			String lastResult,
			int consecutiveSuccessCount,
			int consecutiveFailureCount) {
	}
}

package com.javareview.reviewunit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

public final class ReviewUnitDtos {

	private ReviewUnitDtos() {
	}

	public record AdmitReviewUnitsRequest(
			@Size(max = 500)
			List<UUID> reviewUnitIds) {
	}

	public record ReviewUnitsResponse(
			UUID topicId,
			String topicTitle,
			String domainName,
			long totalCount,
			long admittedCount,
			long pendingFirstReviewCount,
			long activeCount,
			List<ReviewUnitSummaryResponse> units) {
	}

	public record ReviewUnitSummaryResponse(
			UUID reviewUnitId,
			String title,
			int importance,
			int difficulty,
			int interviewFrequency,
			String autoPlanTier,
			BigDecimal mastery,
			String pointStatus,
			UUID stateId,
			String stateStatus,
			Instant admittedAt,
			Instant firstReviewedAt,
			Instant lastReviewedAt,
			Instant nextReviewAt,
			String lastResult,
			int consecutiveSuccessCount,
			int consecutiveFailureCount,
			List<String> weakPoints,
			String nextProbe) {
	}
}

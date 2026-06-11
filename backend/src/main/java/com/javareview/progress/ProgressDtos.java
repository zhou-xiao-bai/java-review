package com.javareview.progress;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProgressDtos {

	private ProgressDtos() {
	}

	public record ProgressOverviewResponse(
			BigDecimal overallMastery,
			long selectedTopicCount,
			long reviewPointCount,
			long unstablePointCount,
			long dueReviewPointCount,
			long completedSessionCount,
			long openWeaknessCount,
			long highRiskPointCount,
			long autoPlannableTopicCount) {
	}

	public record DomainProgressResponse(
			UUID domainId,
			String domainName,
			long topicCount,
			long reviewPointCount,
			BigDecimal averageMastery,
			long unstablePointCount,
			long duePointCount,
			long stablePointCount,
			long uncoveredPointCount,
			long openWeaknessCount) {
	}

	public record TopicProgressResponse(
			UUID topicId,
			String topicTitle,
			String domainName,
			String status,
			String relevanceTier,
			boolean planEnabled,
			int interviewValue,
			long reviewPointCount,
			long unstablePointCount,
			long duePointCount,
			long stablePointCount,
			long uncoveredPointCount,
			long openWeaknessCount,
			BigDecimal averageMastery,
			Instant nextReviewAt,
			List<String> weakPointSummary) {
	}

	public record WeakPointResponse(
			String weakPoint,
			String category,
			String evidence,
			int severity,
			String status,
			String topicTitle,
			String pointTitle,
			BigDecimal mastery,
			Instant createdAt) {
	}

	public record DueReviewPointResponse(
			UUID reviewPointId,
			String topicTitle,
			String pointTitle,
			String status,
			BigDecimal mastery,
			Instant nextReviewAt,
			String dueReason,
			String nextProbe) {
	}

	public record RecentSessionResponse(
			UUID sessionId,
			String topicTitle,
			String pointTitle,
			String status,
			BigDecimal finalScore,
			Instant startedAt,
			Instant endedAt) {
	}

	public record ReviewPlanCalendarResponse(
			LocalDate startDate,
			LocalDate endDate,
			List<ReviewPlanDayResponse> days) {
	}

	public record ReviewPlanDayResponse(
			LocalDate date,
			int itemCount,
			int estimatedMinutes,
			List<ReviewPlanItemResponse> items) {
	}

	public record ReviewPlanItemResponse(
			UUID reviewUnitStateId,
			UUID reviewPointId,
			String source,
			String type,
			String typeLabel,
			String planReason,
			String status,
			String statusLabel,
			String domainName,
			String topicTitle,
			String pointTitle,
			int estimatedMinutes,
			Instant nextReviewAt,
			String dueStatus) {
	}
}

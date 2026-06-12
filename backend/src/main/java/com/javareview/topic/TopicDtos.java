package com.javareview.topic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TopicDtos {

	private TopicDtos() {
	}

	public record TopicsResponse(List<DomainTopicsResponse> domains, TopicTotalsResponse totals) {
	}

	public record DomainTopicsResponse(
			UUID id,
			String code,
			String name,
			long topicCount,
			long selectedCount,
			List<TopicSummaryResponse> topics) {
	}

	public record TopicSummaryResponse(
			UUID id,
			UUID domainId,
			String domainName,
			String code,
			String title,
			String source,
			boolean selected,
			String relevanceTier,
			boolean planEnabled,
			int interviewValue,
			int newExpansionLimit,
			long reviewPointCount,
			long coveredReviewPointCount,
			long admittedReviewUnitCount,
			long pendingFirstReviewUnitCount,
			long reviewedReviewUnitCount,
			BigDecimal averageMastery,
			Instant nextReviewAt,
			List<String> weakPointSummary) {
	}

	public record TopicTotalsResponse(
			long domainCount,
			long topicCount,
			long selectedTopicCount,
			long reviewPointCount,
			BigDecimal averageMastery) {
	}

	public record CreateTopicRequest(
			@NotNull
			UUID domainId,
			@NotBlank
			@Size(max = 120)
			String title) {
	}

	public record UpdateTopicSelectionRequest(
			@NotNull
			Boolean selected) {
	}

	public record UpdateTopicSelectionsRequest(
			@NotEmpty
			@Size(max = 500)
			List<UUID> topicIds,
			@NotNull
			Boolean selected) {
	}

	public record UpdateTopicPlanningRequest(
			@NotNull
			RelevanceTier relevanceTier,
			@NotNull
			Boolean planEnabled,
			@NotNull
			@Min(1)
			@Max(5)
			Integer interviewValue,
			@NotNull
			@Min(0)
			@Max(20)
			Integer newExpansionLimit) {
	}
}

package com.javareview.topic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
			long reviewPointCount,
			long coveredReviewPointCount,
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
}

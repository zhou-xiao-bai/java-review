package com.javareview.today;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TodayDtos {

	private TodayDtos() {
	}

	public record TodayPlanResponse(
			LocalDate date,
			int capacityMinutes,
			int scheduledMinutes,
			int completedMinutes,
			int remainingMinutes,
			TodaySummaryResponse summary,
			List<TaskGroupResponse> groups) {
	}

	public record TodaySummaryResponse(
			SummaryMetricResponse carryOver,
			SummaryMetricResponse due,
			SummaryMetricResponse newExpansion,
			SummaryMetricResponse manual) {
	}

	public record SummaryMetricResponse(long count, int minutes) {
	}

	public record TaskGroupResponse(
			String type,
			String label,
			long count,
			int scheduledMinutes,
			List<ReviewTaskResponse> tasks) {
	}

	public record ReviewTaskResponse(
			UUID id,
			UUID reviewPointId,
			UUID topicId,
			String topicTitle,
			String domainName,
			String pointTitle,
			String manualPrompt,
			LocalDate date,
			String type,
			String typeLabel,
			String status,
			String statusLabel,
			BigDecimal priorityScore,
			int estimatedMinutes,
			String dueStatus,
			Instant nextReviewAt,
			Instant createdAt,
			Instant completedAt,
			Instant removedAt) {
	}

	public record CreateManualTaskRequest(
			@NotBlank
			@Size(max = 1000)
			String prompt,
			@Min(1)
			@Max(120)
			Integer estimatedMinutes) {
	}

	public record RemoveReviewTasksRequest(
			@NotEmpty
			List<@NotNull UUID> taskIds) {
	}
}

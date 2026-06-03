package com.javareview.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

	private ProjectDtos() {
	}

	public record ProjectCaseRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 4000) String background,
			@Size(max = 4000) String responsibility,
			List<String> techStack,
			List<String> highlights) {
	}

	public record ProjectCaseResponse(
			UUID id,
			String name,
			String background,
			String responsibility,
			List<String> techStack,
			List<String> highlights,
			List<String> weakPoints,
			Instant createdAt,
			Instant updatedAt) {
	}

	public record ProjectAnswerRequest(@NotBlank @Size(max = 8000) String answer) {
	}

	public record ProjectSessionResponse(
			UUID id,
			UUID projectCaseId,
			String status,
			BigDecimal finalScore,
			ProjectEvaluation evaluation,
			List<String> suggestedTopics,
			List<ProjectTurnResponse> turns) {
	}

	public record ProjectTurnResponse(UUID id, String role, String content, Instant createdAt) {
	}
}

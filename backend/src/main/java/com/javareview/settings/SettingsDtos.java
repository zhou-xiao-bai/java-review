package com.javareview.settings;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

	private SettingsDtos() {
	}

	public record SettingsResponse(
			String llmProvider,
			String llmBaseUrl,
			String llmApiKeyMasked,
			boolean llmApiKeyConfigured,
			String llmModel,
			String activeLlmConfigId,
			List<LlmConfigResponse> llmConfigs,
			int requestTimeoutSeconds,
			int dailyReviewMinutes) {
	}

	public record LlmConfigResponse(
			String id,
			String name,
			String provider,
			String baseUrl,
			String apiKeyMasked,
			boolean apiKeyConfigured,
			String model) {
	}

	public record UpdateSettingsRequest(
			@NotBlank
			@Size(max = 80)
			String activeLlmConfigId,
			@NotEmpty
			List<LlmConfigRequest> llmConfigs,
			@Min(1)
			@Max(300)
			int requestTimeoutSeconds,
			@Min(10)
			@Max(240)
			int dailyReviewMinutes) {
	}

	public record LlmConfigRequest(
			@NotBlank
			@Size(max = 80)
			String id,
			@NotBlank
			@Size(max = 80)
			String name,
			@NotBlank
			@Size(max = 40)
			String provider,
			@Size(max = 500)
			String baseUrl,
			@Size(max = 2000)
			String apiKey,
			@NotBlank
			@Size(max = 120)
			String model) {
	}

	public record LlmTestResponse(boolean success, String message, String provider, String model) {
	}
}

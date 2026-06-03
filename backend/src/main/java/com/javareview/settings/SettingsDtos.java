package com.javareview.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
			int requestTimeoutSeconds,
			int dailyReviewMinutes) {
	}

	public record UpdateSettingsRequest(
			@NotBlank
			@Size(max = 40)
			String llmProvider,
			@Size(max = 500)
			String llmBaseUrl,
			@Size(max = 2000)
			String llmApiKey,
			@NotBlank
			@Size(max = 120)
			String llmModel,
			@Min(1)
			@Max(300)
			int requestTimeoutSeconds,
			@Min(10)
			@Max(240)
			int dailyReviewMinutes) {
	}

	public record LlmTestResponse(boolean success, String message, String provider, String model) {
	}
}

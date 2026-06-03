package com.javareview.settings;

public record LlmConfig(
		String id,
		String name,
		String provider,
		String baseUrl,
		String apiKey,
		String model) {
}

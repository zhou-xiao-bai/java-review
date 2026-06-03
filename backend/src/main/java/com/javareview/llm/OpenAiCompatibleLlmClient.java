package com.javareview.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.UserSettings;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private final WebClient.Builder webClientBuilder;

	public OpenAiCompatibleLlmClient(WebClient.Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}

	@Override
	public LlmResult complete(UserSettings settings, String systemPrompt, String userPrompt) {
		if (!configured(settings)) {
			return LlmResult.failure("LLM API key is not configured; local fallback is used.");
		}
		try {
			JsonNode response = webClientBuilder
					.baseUrl(normalizeBaseUrl(settings.getLlmBaseUrl()))
					.build()
					.post()
					.uri("/chat/completions")
					.header("Authorization", "Bearer " + settings.getLlmApiKey())
					.bodyValue(Map.of(
							"model", settings.getLlmModel(),
							"temperature", 0.2,
							"messages", List.of(
									Map.of("role", "system", "content", systemPrompt),
									Map.of("role", "user", "content", userPrompt))))
					.retrieve()
					.bodyToMono(JsonNode.class)
					.block(Duration.ofSeconds(settings.getRequestTimeoutSeconds()));
			String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
			if (content == null || content.isBlank()) {
				return LlmResult.failure("LLM response did not contain message content.");
			}
			return LlmResult.success(content);
		}
		catch (RuntimeException exception) {
			return LlmResult.failure("LLM request failed: " + exception.getMessage());
		}
	}

	@Override
	public LlmTestResponse test(UserSettings settings) {
		if (!configured(settings)) {
			return new LlmTestResponse(true, "未配置 API Key，将使用本地结构化 fallback。", settings.getLlmProvider(), settings.getLlmModel());
		}
		LlmResult result = complete(settings, "Return only OK.", "Respond with OK.");
		return new LlmTestResponse(result.success(), result.success() ? "连接测试成功。" : result.errorMessage(),
				settings.getLlmProvider(), settings.getLlmModel());
	}

	private static boolean configured(UserSettings settings) {
		return settings.getLlmApiKey() != null && !settings.getLlmApiKey().isBlank();
	}

	private static String normalizeBaseUrl(String baseUrl) {
		String resolved = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
		return resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
	}
}

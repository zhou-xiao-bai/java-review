package com.javareview.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.UserSettings;

import reactor.core.publisher.Mono;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper;

	public OpenAiCompatibleLlmClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
		this.webClientBuilder = webClientBuilder;
		this.objectMapper = objectMapper;
	}

	@Override
	public LlmResult complete(UserSettings settings, String systemPrompt, String userPrompt) {
		if (!configured(settings)) {
			return LlmResult.failure("LLM API key is not configured; local fallback is used.");
		}
		try {
			String baseUrl = normalizeBaseUrl(settings.getLlmBaseUrl());
			String endpoint = baseUrl + "/chat/completions";
			LlmResult result = webClientBuilder
					.baseUrl(baseUrl)
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
					.exchangeToMono(response -> parseResponse(response, endpoint))
					.block(Duration.ofSeconds(settings.getRequestTimeoutSeconds()));
			return result == null ? LlmResult.failure("LLM request returned an empty response.") : result;
		}
		catch (RuntimeException exception) {
			return LlmResult.failure("LLM request failed: " + exception.getMessage());
		}
	}

	private Mono<LlmResult> parseResponse(ClientResponse response, String endpoint) {
		MediaType contentType = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
		return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
			if (response.statusCode().isError()) {
				return LlmResult.failure("LLM request failed: HTTP " + response.statusCode().value() + " from " + endpoint + " - " + summarize(body));
			}
			if (!isJson(contentType)) {
				return LlmResult.failure("LLM endpoint returned " + contentType + " from " + endpoint + ". 请确认 Base URL 是 OpenAI-compatible API 根路径，例如 https://lpgpt.us/v1，而不是网页地址。");
			}
			JsonNode json = readJson(body);
			String content = json.at("/choices/0/message/content").asText(null);
			if (content == null || content.isBlank()) {
				return LlmResult.failure("LLM response did not contain message content.");
			}
			return LlmResult.success(content);
		});
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

	private static boolean isJson(MediaType contentType) {
		return MediaType.APPLICATION_JSON.includes(contentType) || contentType.getSubtype().endsWith("+json");
	}

	private JsonNode readJson(String body) {
		try {
			return objectMapper.readTree(body);
		}
		catch (Exception exception) {
			throw new IllegalArgumentException("LLM response was not valid JSON: " + exception.getMessage(), exception);
		}
	}

	private static String summarize(String body) {
		String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
		if (normalized.isEmpty()) {
			return "empty response body";
		}
		return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
	}

	private static String normalizeBaseUrl(String baseUrl) {
		String resolved = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
		return resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
	}
}

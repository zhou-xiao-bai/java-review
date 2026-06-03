package com.javareview.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.llm.LlmClient;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.SettingsDtos.SettingsResponse;
import com.javareview.settings.SettingsDtos.UpdateSettingsRequest;

@Service
public class SettingsService {

	private static final String MASKED_KEY_SENTINEL = "********";

	private final UserSettingsRepository settingsRepository;
	private final LlmClient llmClient;

	public SettingsService(UserSettingsRepository settingsRepository, LlmClient llmClient) {
		this.settingsRepository = settingsRepository;
		this.llmClient = llmClient;
	}

	@Transactional(readOnly = true)
	public SettingsResponse getSettings(User user) {
		return toResponse(findOrDefault(user));
	}

	@Transactional
	public SettingsResponse updateSettings(User user, UpdateSettingsRequest request) {
		UserSettings settings = findOrDefault(user);
		String apiKey = normalizeOptional(request.llmApiKey());
		boolean replaceApiKey = apiKey != null && !MASKED_KEY_SENTINEL.equals(apiKey);
		settings.update(
				trimRequired(request.llmProvider(), "llmProvider"),
				normalizeOptional(request.llmBaseUrl()),
				apiKey,
				replaceApiKey,
				trimRequired(request.llmModel(), "llmModel"),
				request.requestTimeoutSeconds(),
				request.dailyReviewMinutes());
		return toResponse(settingsRepository.save(settings));
	}

	@Transactional(readOnly = true)
	public LlmTestResponse testLlm(User user) {
		UserSettings settings = findOrDefault(user);
		return llmClient.test(settings);
	}

	@Transactional(readOnly = true)
	public UserSettings findOrDefault(User user) {
		return settingsRepository.findByUserId(user.getId()).orElseGet(() -> new UserSettings(user));
	}

	private static SettingsResponse toResponse(UserSettings settings) {
		String apiKey = settings.getLlmApiKey();
		return new SettingsResponse(
				settings.getLlmProvider(),
				settings.getLlmBaseUrl(),
				mask(apiKey),
				apiKey != null && !apiKey.isBlank(),
				settings.getLlmModel(),
				settings.getRequestTimeoutSeconds(),
				settings.getDailyReviewMinutes());
	}

	private static String mask(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			return "";
		}
		String trimmed = apiKey.trim();
		if (trimmed.length() <= 8) {
			return "****";
		}
		return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
	}

	private static String trimRequired(String value, String field) {
		String normalized = normalizeOptional(value);
		if (normalized == null) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return normalized;
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}

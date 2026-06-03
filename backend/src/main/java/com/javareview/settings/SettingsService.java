package com.javareview.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.llm.LlmClient;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.SettingsDtos.LlmConfigResponse;
import com.javareview.settings.SettingsDtos.SettingsResponse;
import com.javareview.settings.SettingsDtos.UpdateSettingsRequest;

import jakarta.persistence.EntityManager;

@Service
public class SettingsService {

	private static final String MASKED_KEY_SENTINEL = "********";

	private final UserSettingsRepository settingsRepository;
	private final LlmClient llmClient;
	private final EntityManager entityManager;

	public SettingsService(UserSettingsRepository settingsRepository, LlmClient llmClient, EntityManager entityManager) {
		this.settingsRepository = settingsRepository;
		this.llmClient = llmClient;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public SettingsResponse getSettings(User user) {
		return toResponse(findOrDefault(user));
	}

	@Transactional
	public SettingsResponse updateSettings(User user, UpdateSettingsRequest request) {
		UserSettings settings = findOrDefault(user);
		List<LlmConfig> configs = normalizeConfigs(settings, request);
		settings.update(
				trimRequired(request.activeLlmConfigId(), "activeLlmConfigId"),
				configs,
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
		return settingsRepository.findByUserId(user.getId())
				.orElseGet(() -> new UserSettings(entityManager.getReference(User.class, user.getId())));
	}

	private static SettingsResponse toResponse(UserSettings settings) {
		String apiKey = settings.getLlmApiKey();
		return new SettingsResponse(
				settings.getLlmProvider(),
				settings.getLlmBaseUrl(),
				mask(apiKey),
				apiKey != null && !apiKey.isBlank(),
				settings.getLlmModel(),
				settings.getActiveLlmConfigId(),
				settings.getLlmConfigs().stream().map(SettingsService::toConfigResponse).toList(),
				settings.getRequestTimeoutSeconds(),
				settings.getDailyReviewMinutes());
	}

	private static LlmConfigResponse toConfigResponse(LlmConfig config) {
		String apiKey = config.apiKey();
		return new LlmConfigResponse(
				config.id(),
				config.name(),
				config.provider(),
				config.baseUrl(),
				mask(apiKey),
				apiKey != null && !apiKey.isBlank(),
				config.model());
	}

	private static List<LlmConfig> normalizeConfigs(UserSettings settings, UpdateSettingsRequest request) {
		Map<String, LlmConfig> existingById = settings.getLlmConfigs().stream()
				.collect(Collectors.toMap(LlmConfig::id, Function.identity(), (left, right) -> left));
		HashSet<String> ids = new HashSet<>();
		List<LlmConfig> configs = request.llmConfigs().stream().map(config -> {
			String id = trimRequired(config.id(), "llmConfigs.id");
			if (!ids.add(id)) {
				throw new IllegalArgumentException("LLM config id must be unique.");
			}
			String apiKey = normalizeOptional(config.apiKey());
			if (apiKey != null && MASKED_KEY_SENTINEL.equals(apiKey)) {
				apiKey = existingById.get(id) == null ? null : existingById.get(id).apiKey();
			}
			return new LlmConfig(
					id,
					trimRequired(config.name(), "llmConfigs.name"),
					trimRequired(config.provider(), "llmConfigs.provider"),
					normalizeOptional(config.baseUrl()),
					apiKey,
					trimRequired(config.model(), "llmConfigs.model"));
		}).toList();
		boolean activeExists = configs.stream().anyMatch(config -> config.id().equals(request.activeLlmConfigId()));
		if (!activeExists) {
			throw new IllegalArgumentException("activeLlmConfigId must reference an existing LLM config.");
		}
		return configs;
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

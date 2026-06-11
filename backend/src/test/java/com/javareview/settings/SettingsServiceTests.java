package com.javareview.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.llm.LlmClient;
import com.javareview.settings.SettingsDtos.UpdateSettingsRequest;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTests {

	@Mock
	private UserSettingsRepository settingsRepository;

	@Mock
	private EntityManager entityManager;

	private SettingsService settingsService;
	private User user;

	@BeforeEach
	void setUp() {
		settingsService = new SettingsService(settingsRepository, mock(LlmClient.class), entityManager);
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
	}

	@Test
	void defaultSettingsUseManagedUserReference() {
		User managedUser = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
		when(entityManager.getReference(User.class, user.getId())).thenReturn(managedUser);

		UserSettings settings = settingsService.findOrDefault(user);

		assertThat(settings.getUser()).isSameAs(managedUser);
	}

	@Test
	void apiKeyIsMaskedWhenSettingsAreRead() {
		UserSettings settings = new UserSettings(user);
		settings.update("default", List.of(new LlmConfig("default", "默认", "openai-compatible", "https://api.example.com/v1", "sk-test-secret-value", "gpt-test")), 30, 60);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));

		var response = settingsService.getSettings(user);

		assertThat(response.llmApiKeyConfigured()).isTrue();
		assertThat(response.llmApiKeyMasked()).isEqualTo("sk-t****alue");
		assertThat(response.llmApiKeyMasked()).doesNotContain("secret");
		assertThat(response.llmConfigs()).hasSize(1);
		assertThat(response.llmConfigs().getFirst().apiKeyMasked()).isEqualTo("sk-t****alue");
		assertThat(response.dailyReviewMinutes()).isEqualTo(60);
	}

	@Test
	void maskedSentinelKeepsExistingApiKey() {
		UserSettings settings = new UserSettings(user);
		settings.update("default", List.of(new LlmConfig("default", "默认", "openai-compatible", "https://api.example.com/v1", "sk-original", "gpt-test")), 30, 60);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
		when(settingsRepository.save(settings)).thenReturn(settings);

		settingsService.updateSettings(user, new UpdateSettingsRequest(
				"default",
				List.of(new SettingsDtos.LlmConfigRequest("default", "默认", "openai-compatible", "https://api.example.com/v1", "********", "gpt-next")),
				40,
				70));

		assertThat(settings.getLlmApiKey()).isEqualTo("sk-original");
		assertThat(settings.getLlmModel()).isEqualTo("gpt-next");
	}

	@Test
	void activeLlmConfigSelectsCurrentEndpoint() {
		UserSettings settings = new UserSettings(user);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
		when(settingsRepository.save(settings)).thenReturn(settings);

		settingsService.updateSettings(user, new UpdateSettingsRequest(
				"backup",
				List.of(
						new SettingsDtos.LlmConfigRequest("primary", "主站", "openai-compatible", "https://api.primary.test/v1", "sk-primary", "gpt-a"),
						new SettingsDtos.LlmConfigRequest("backup", "备用", "openai-compatible", "https://api.backup.test/v1", "sk-backup", "gpt-b")),
				30,
				60));

		assertThat(settings.getActiveLlmConfigId()).isEqualTo("backup");
		assertThat(settings.getLlmBaseUrl()).isEqualTo("https://api.backup.test/v1");
		assertThat(settings.getLlmApiKey()).isEqualTo("sk-backup");
		assertThat(settings.getLlmModel()).isEqualTo("gpt-b");
	}

	@Test
	void dailyReviewMinutesCanBeUpdated() {
		UserSettings settings = new UserSettings(user);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
		when(settingsRepository.save(settings)).thenReturn(settings);

		var response = settingsService.updateSettings(user, new UpdateSettingsRequest(
				"default",
				List.of(new SettingsDtos.LlmConfigRequest("default", "默认", "openai-compatible", "https://api.example.com/v1", "", "gpt-test")),
				30,
				90));

		assertThat(settings.getDailyReviewMinutes()).isEqualTo(90);
		assertThat(response.dailyReviewMinutes()).isEqualTo(90);
	}
}

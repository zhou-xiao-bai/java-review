package com.javareview.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
		settings.update("openai-compatible", "https://api.example.com/v1", "sk-test-secret-value", true, "gpt-test", 30, 60);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));

		var response = settingsService.getSettings(user);

		assertThat(response.llmApiKeyConfigured()).isTrue();
		assertThat(response.llmApiKeyMasked()).isEqualTo("sk-t****alue");
		assertThat(response.llmApiKeyMasked()).doesNotContain("secret");
	}

	@Test
	void maskedSentinelKeepsExistingApiKey() {
		UserSettings settings = new UserSettings(user);
		settings.update("openai-compatible", "https://api.example.com/v1", "sk-original", true, "gpt-test", 30, 60);
		when(settingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
		when(settingsRepository.save(settings)).thenReturn(settings);

		settingsService.updateSettings(user, new UpdateSettingsRequest(
				"openai-compatible", "https://api.example.com/v1", "********", "gpt-next", 40, 70));

		assertThat(settings.getLlmApiKey()).isEqualTo("sk-original");
		assertThat(settings.getLlmModel()).isEqualTo("gpt-next");
	}
}

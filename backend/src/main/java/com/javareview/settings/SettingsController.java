package com.javareview.settings;

import jakarta.validation.Valid;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.SettingsDtos.SettingsResponse;
import com.javareview.settings.SettingsDtos.UpdateSettingsRequest;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	private final SettingsService settingsService;
	private final AuthService authService;

	public SettingsController(SettingsService settingsService, AuthService authService) {
		this.settingsService = settingsService;
		this.authService = authService;
	}

	@GetMapping
	public SettingsResponse getSettings(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return settingsService.getSettings(currentUser(principal));
	}

	@PutMapping
	public SettingsResponse updateSettings(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody UpdateSettingsRequest request) {
		return settingsService.updateSettings(currentUser(principal), request);
	}

	@PostMapping("/llm/test")
	public LlmTestResponse testLlm(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return settingsService.testLlm(currentUser(principal));
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

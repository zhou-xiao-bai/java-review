package com.javareview.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record BootstrapStatusResponse(boolean initialized) {
	}

	public record BootstrapAdminRequest(
			@NotBlank
			@Size(min = 3, max = 64)
			String username,
			@Email
			String email,
			@NotBlank
			@Size(min = 8, max = 128)
			String password,
			@NotBlank
			@Size(max = 120)
			String displayName) {
	}

	public record LoginRequest(
			@NotBlank
			String identifier,
			@NotBlank
			String password,
			boolean rememberMe) {
	}

	public record CurrentUserResponse(
			UUID id,
			String username,
			String email,
			String displayName,
			String role) {

		static CurrentUserResponse from(User user) {
			return new CurrentUserResponse(
					user.getId(),
					user.getUsername(),
					user.getEmail(),
					user.getDisplayName(),
					user.getRole().name());
		}
	}
}

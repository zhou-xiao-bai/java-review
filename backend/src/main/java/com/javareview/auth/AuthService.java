package com.javareview.auth;

import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.AuthDtos.BootstrapAdminRequest;
import com.javareview.auth.AuthDtos.CurrentUserResponse;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public boolean isInitialized() {
		return userRepository.count() > 0;
	}

	@Transactional
	public CurrentUserResponse bootstrapAdmin(BootstrapAdminRequest request) {
		if (isInitialized()) {
			throw new IllegalStateException("Administrator has already been initialized.");
		}

		String username = normalizeRequired(request.username(), "username");
		String email = normalizeOptional(request.email());
		String displayName = trimRequired(request.displayName(), "displayName");

		if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
			throw new IllegalArgumentException("Email is already in use.");
		}

		if (userRepository.existsByUsernameIgnoreCase(username)) {
			throw new IllegalArgumentException("Username is already in use.");
		}

		User user = new User(
				username,
				email,
				passwordEncoder.encode(request.password()),
				displayName,
				UserRole.ADMIN);

		return CurrentUserResponse.from(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public User requireUserByIdentifier(String identifier) {
		String normalized = normalizeRequired(identifier, "identifier");
		return userRepository.findByUsernameIgnoreCase(normalized)
				.or(() -> userRepository.findByEmailIgnoreCase(normalized))
				.filter(User::isEnabled)
				.orElseThrow(() -> new BadCredentialsException("Invalid username/email or password."));
	}

	private static String normalizeRequired(String value, String field) {
		String normalized = normalizeOptional(value);
		if (normalized == null) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return normalized;
	}

	private static String trimRequired(String value, String field) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(field + " is required.");
		}
		return value.trim();
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.toLowerCase(Locale.ROOT);
	}
}

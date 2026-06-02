package com.javareview.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.javareview.auth.AuthDtos.BootstrapAdminRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

	@Mock
	private UserRepository userRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder);
	}

	@Test
	void bootstrapAdminFailsWhenUserAlreadyExists() {
		when(userRepository.count()).thenReturn(1L);

		assertThatThrownBy(() -> authService.bootstrapAdmin(new BootstrapAdminRequest(
				"admin",
				"admin@example.com",
				"password123",
				"Admin")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already");
	}

	@Test
	void bootstrapAdminCreatesEnabledAdminWithHashedPassword() {
		when(userRepository.count()).thenReturn(0L);
		when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(false);
		when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		authService.bootstrapAdmin(new BootstrapAdminRequest(
				" Admin ",
				" Admin@Example.com ",
				"password123",
				"Admin User"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		org.mockito.Mockito.verify(userRepository).save(captor.capture());
		User saved = captor.getValue();

		assertThat(saved.getUsername()).isEqualTo("admin");
		assertThat(saved.getEmail()).isEqualTo("admin@example.com");
		assertThat(saved.getDisplayName()).isEqualTo("Admin User");
		assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(saved.isEnabled()).isTrue();
		assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void requireUserByIdentifierSupportsUsernameOrEmail() {
		User user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		when(userRepository.findByUsernameIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));

		assertThat(authService.requireUserByIdentifier("Admin@Example.com")).isSameAs(user);
	}
}

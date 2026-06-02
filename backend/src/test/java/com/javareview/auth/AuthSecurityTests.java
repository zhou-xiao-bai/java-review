package com.javareview.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.javareview.common.ApiExceptionHandler;
import com.javareview.common.HealthController;
import com.javareview.common.SecurityConfig;

@WebMvcTest(
		controllers = { AuthController.class, HealthController.class },
		excludeAutoConfiguration = {
				DataSourceAutoConfiguration.class,
				HibernateJpaAutoConfiguration.class,
				FlywayAutoConfiguration.class
		})
@Import({ SecurityConfig.class, ApiExceptionHandler.class, AuthSecurityTests.TestSecurityProperties.class })
class AuthSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private AuthenticationManager authenticationManager;

	@MockitoBean
	private RememberMeServices rememberMeServices;

	@MockitoBean
	private ApplicationUserDetailsService applicationUserDetailsService;

	@Test
	void healthCheckIsPublic() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk());
	}

	@Test
	void currentUserRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithRememberMeInvokesRememberMeServices() throws Exception {
		User user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		Authentication authentication = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
		when(authService.requireUserByIdentifier("admin")).thenReturn(user);
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authentication);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "identifier": "admin",
						  "password": "password123",
						  "rememberMe": true
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"));

		verify(rememberMeServices).loginSuccess(any(), any(), same(authentication));
	}

	@Test
	void loginWithoutRememberMeSkipsRememberMeServices() throws Exception {
		User user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		Authentication authentication = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
		when(authService.requireUserByIdentifier("admin")).thenReturn(user);
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authentication);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "identifier": "admin",
						  "password": "password123",
						  "rememberMe": false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"));

		verify(rememberMeServices, never()).loginSuccess(any(), any(), any());
	}

	@TestConfiguration
	static class TestSecurityProperties {

		@Bean
		RememberMeProperties rememberMeProperties() {
			return new RememberMeProperties("test-key", 3600);
		}
	}
}

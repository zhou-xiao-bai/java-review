package com.javareview.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.authentication.RememberMeServices;

import com.javareview.auth.ApplicationUserDetailsService;
import com.javareview.auth.RememberMeProperties;

class SecurityConfigTests {

	@Test
	void rememberMeServiceWritesCookieWithoutFormParameter() {
		SecurityConfig securityConfig = new SecurityConfig();
		RememberMeServices rememberMeServices = securityConfig.rememberMeServices(
				mock(ApplicationUserDetailsService.class),
				new RememberMeProperties("test-key", 3600));
		MockHttpServletResponse response = new MockHttpServletResponse();

		rememberMeServices.loginSuccess(
				new MockHttpServletRequest(),
				response,
				new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN"));

		Cookie cookie = response.getCookie("remember-me");
		assertThat(cookie).isNotNull();
		assertThat(cookie.getMaxAge()).isEqualTo(3600);
	}
}

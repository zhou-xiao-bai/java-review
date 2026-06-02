package com.javareview.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.remember-me")
public record RememberMeProperties(
		String key,
		int tokenValiditySeconds) {
}

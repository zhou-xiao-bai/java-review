package com.javareview.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.settings.UserSettings;
import com.javareview.settings.LlmConfig;
import com.sun.net.httpserver.HttpServer;

class OpenAiCompatibleLlmClientTests {

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void reportsHtmlResponseAsInvalidEndpoint() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] body = "<html>login</html>".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(WebClient.builder(), new ObjectMapper());
		UserSettings settings = new UserSettings(new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN));
		settings.update("default", List.of(new LlmConfig("default", "默认", "openai-compatible", "http://localhost:" + server.getAddress().getPort(), "sk-test", "gpt-test")), 5, 60);

		LlmResult result = client.complete(settings, "system", "user");

		assertThat(result.success()).isFalse();
		assertThat(result.errorMessage()).contains("text/html", "OpenAI-compatible API 根路径");
	}
}

package com.javareview.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = HealthController.class,
		excludeAutoConfiguration = {
				DataSourceAutoConfiguration.class,
				HibernateJpaAutoConfiguration.class,
				FlywayAutoConfiguration.class
		})
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsHealthStatusWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.application").value("java-review"));
	}
}

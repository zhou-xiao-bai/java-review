package com.javareview.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.llm.LlmClient;
import com.javareview.llm.LlmResult;
import com.javareview.project.ProjectDtos.ProjectAnswerRequest;
import com.javareview.project.ProjectDtos.ProjectCaseRequest;
import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

	@Mock
	private ProjectCaseRepository projectCaseRepository;

	@Mock
	private ProjectSessionRepository projectSessionRepository;

	@Mock
	private ProjectTurnRepository projectTurnRepository;

	@Mock
	private SettingsService settingsService;

	private ProjectService projectService;
	private User user;

	@BeforeEach
	void setUp() {
		projectService = new ProjectService(
				projectCaseRepository,
				projectSessionRepository,
				projectTurnRepository,
				settingsService,
				new TestLlmClient(),
				Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
	}

	@Test
	void createsProjectCase() {
		when(projectCaseRepository.save(any(ProjectCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var response = projectService.create(user, new ProjectCaseRequest(
				"订单系统改造", "高并发下单", "负责库存扣减", List.of("Redis", "MySQL"), List.of("缓存一致性")));

		assertThat(response.name()).isEqualTo("订单系统改造");
		assertThat(response.techStack()).contains("Redis", "MySQL");
	}

	@Test
	void longProjectAnswerClosesSessionAndSavesTurn() {
		ProjectCase projectCase = new ProjectCase(user, "订单系统", "背景", "职责", List.of("Redis"), List.of("亮点"));
		ProjectSession session = new ProjectSession(user, projectCase, Instant.parse("2026-06-03T00:00:00Z"));
		when(projectSessionRepository.findByIdAndUserIdWithCase(session.getId(), user.getId())).thenReturn(Optional.of(session));
		when(projectTurnRepository.save(any(ProjectTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(projectTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());
		when(settingsService.findOrDefault(user)).thenReturn(new UserSettings(user));

		var response = projectService.answer(user, session.getId(), new ProjectAnswerRequest(
				"我负责订单链路改造，先评估峰值和库存扣减一致性风险，再用 Redis 预扣减和 MySQL 事务落库保证最终一致，配套监控扣减失败率、延迟、补偿队列积压，并在一次超卖风险中通过日志、binlog 和库存流水定位根因，修复后增加幂等和补偿告警。"));

		assertThat(response.status()).isEqualTo("evaluated");
		assertThat(response.evaluation()).isNotNull();
		assertThat(projectCase.getWeakPoints()).isNotEmpty();
	}

	private static class TestLlmClient implements LlmClient {

		@Override
		public LlmResult complete(UserSettings settings, String systemPrompt, String userPrompt) {
			return LlmResult.failure("fallback");
		}

		@Override
		public LlmTestResponse test(UserSettings settings) {
			return new LlmTestResponse(true, "ok", "test", "test");
		}
	}
}

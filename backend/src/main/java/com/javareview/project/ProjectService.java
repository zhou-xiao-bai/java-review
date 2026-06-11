package com.javareview.project;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.llm.LlmClient;
import com.javareview.project.ProjectDtos.ProjectAnswerRequest;
import com.javareview.project.ProjectDtos.ProjectCaseRequest;
import com.javareview.project.ProjectDtos.ProjectCaseResponse;
import com.javareview.project.ProjectDtos.ProjectSessionResponse;
import com.javareview.project.ProjectDtos.ProjectTurnResponse;
import com.javareview.project.ProjectEvaluation.ProjectScore;
import com.javareview.settings.SettingsService;

@Service
public class ProjectService {

	private final ProjectCaseRepository projectCaseRepository;
	private final ProjectSessionRepository projectSessionRepository;
	private final ProjectTurnRepository projectTurnRepository;
	private final SettingsService settingsService;
	private final LlmClient llmClient;
	private final Clock clock;

	public ProjectService(
			ProjectCaseRepository projectCaseRepository,
			ProjectSessionRepository projectSessionRepository,
			ProjectTurnRepository projectTurnRepository,
			SettingsService settingsService,
			LlmClient llmClient,
			Clock clock) {
		this.projectCaseRepository = projectCaseRepository;
		this.projectSessionRepository = projectSessionRepository;
		this.projectTurnRepository = projectTurnRepository;
		this.settingsService = settingsService;
		this.llmClient = llmClient;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ProjectCaseResponse> list(User user) {
		return projectCaseRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toCaseResponse).toList();
	}

	@Transactional(readOnly = true)
	public ProjectCaseResponse get(User user, UUID id) {
		return toCaseResponse(requireCase(user, id));
	}

	@Transactional
	public ProjectCaseResponse create(User user, ProjectCaseRequest request) {
		ProjectCase projectCase = new ProjectCase(user, trim(request.name()), request.background(), request.responsibility(), clean(request.techStack()), clean(request.highlights()));
		return toCaseResponse(projectCaseRepository.save(projectCase));
	}

	@Transactional
	public ProjectCaseResponse update(User user, UUID id, ProjectCaseRequest request) {
		ProjectCase projectCase = requireCase(user, id);
		projectCase.update(trim(request.name()), request.background(), request.responsibility(), clean(request.techStack()), clean(request.highlights()));
		return toCaseResponse(projectCase);
	}

	@Transactional
	public void delete(User user, UUID id) {
		projectCaseRepository.delete(requireCase(user, id));
	}

	@Transactional
	public ProjectSessionResponse start(User user, UUID projectCaseId) {
		ProjectCase projectCase = requireCase(user, projectCaseId);
		ProjectSession session = projectSessionRepository.save(new ProjectSession(user, projectCase, Instant.now(clock)));
		projectTurnRepository.save(new ProjectTurn(session, "ai", firstQuestion(projectCase)));
		return toSessionResponse(session);
	}

	@Transactional
	public ProjectSessionResponse answer(User user, UUID sessionId, ProjectAnswerRequest request) {
		ProjectSession session = requireSession(user, sessionId);
		if (session.getStatus() != ProjectSessionStatus.ACTIVE) {
			throw new IllegalStateException("Project session is not active.");
		}
		projectTurnRepository.save(new ProjectTurn(session, "user", request.answer().trim()));
		String content = llmClient.complete(settingsService.findOrDefault(user), "你是严格的项目面试官。", projectPrompt(session, request.answer())).content();
		if (request.answer().trim().length() < 120) {
			projectTurnRepository.save(new ProjectTurn(session, "ai", content == null || content.isBlank() ? followUp(session) : content));
		}
		else {
			session.evaluate(localEvaluation(session, request.answer()), Instant.now(clock));
			projectTurnRepository.save(new ProjectTurn(session, "ai", session.getEvaluation().overallComment()));
		}
		return toSessionResponse(session);
	}

	private ProjectCase requireCase(User user, UUID id) {
		return projectCaseRepository.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Project case not found."));
	}

	private ProjectSession requireSession(User user, UUID id) {
		return projectSessionRepository.findByIdAndUserIdWithCase(id, user.getId()).orElseThrow(() -> new ResourceNotFoundException("Project session not found."));
	}

	private ProjectCaseResponse toCaseResponse(ProjectCase projectCase) {
		return new ProjectCaseResponse(projectCase.getId(), projectCase.getName(), projectCase.getBackground(), projectCase.getResponsibility(), projectCase.getTechStack(), projectCase.getHighlights(), projectCase.getWeakPoints(), projectCase.getCreatedAt(), projectCase.getUpdatedAt());
	}

	private ProjectSessionResponse toSessionResponse(ProjectSession session) {
		return new ProjectSessionResponse(
				session.getId(),
				session.getProjectCase().getId(),
				session.getStatus().name().toLowerCase(Locale.ROOT),
				session.getFinalScore(),
				session.getEvaluation(),
				session.getSuggestedTopics(),
				projectTurnRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
						.map(turn -> new ProjectTurnResponse(turn.getId(), turn.getRole(), turn.getContent(), turn.getCreatedAt()))
						.toList());
	}

	private static ProjectEvaluation localEvaluation(ProjectSession session, String answer) {
		BigDecimal score = BigDecimal.valueOf(Math.min(4.2, 2.2 + answer.length() / 180.0)).setScale(2, RoundingMode.HALF_UP);
		List<String> weakPoints = List.of("项目指标证据需要更具体", "故障排查闭环需要补充根因和预防");
		List<String> suggestedTopics = session.getProjectCase().getTechStack().stream().limit(3).map(value -> value + " 深挖").toList();
		return new ProjectEvaluation(
				"项目表达已经覆盖背景和职责，后续重点补充量化指标、方案取舍和排查证据。",
				new ProjectScore(score, score, score, score, score, score, score),
				weakPoints,
				suggestedTopics.isEmpty() ? List.of("缓存一致性", "性能排查") : suggestedTopics);
	}

	private static String firstQuestion(ProjectCase projectCase) {
		return "请先用 2 分钟说明项目「" + projectCase.getName() + "」的业务背景、你的职责、核心技术方案和可量化结果。";
	}

	private static String followUp(ProjectSession session) {
		return "请继续补充「" + session.getProjectCase().getName() + "」中的方案取舍、关键指标、线上问题排查证据，以及如果重做会如何优化。";
	}

	private static String projectPrompt(ProjectSession session, String answer) {
		return "项目：" + session.getProjectCase().getName() + "\n回答：" + answer + "\n请追问或收口评价。";
	}

	private static List<String> clean(List<String> values) {
		return values == null ? List.of() : values.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
	}

	private static String trim(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("name is required.");
		}
		return value.trim();
	}
}

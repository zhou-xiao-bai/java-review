package com.javareview.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.project.ProjectDtos.ProjectAnswerRequest;
import com.javareview.project.ProjectDtos.ProjectCaseRequest;
import com.javareview.project.ProjectDtos.ProjectCaseResponse;
import com.javareview.project.ProjectDtos.ProjectSessionResponse;

@RestController
@RequestMapping("/api")
public class ProjectController {

	private final ProjectService projectService;
	private final AuthService authService;

	public ProjectController(ProjectService projectService, AuthService authService) {
		this.projectService = projectService;
		this.authService = authService;
	}

	@GetMapping("/project-cases")
	public List<ProjectCaseResponse> list(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return projectService.list(currentUser(principal));
	}

	@PostMapping("/project-cases")
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectCaseResponse create(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody ProjectCaseRequest request) {
		return projectService.create(currentUser(principal), request);
	}

	@GetMapping("/project-cases/{id}")
	public ProjectCaseResponse get(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return projectService.get(currentUser(principal), id);
	}

	@PutMapping("/project-cases/{id}")
	public ProjectCaseResponse update(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@Valid @RequestBody ProjectCaseRequest request) {
		return projectService.update(currentUser(principal), id, request);
	}

	@DeleteMapping("/project-cases/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		projectService.delete(currentUser(principal), id);
	}

	@PostMapping("/project-cases/{id}/sessions")
	public ProjectSessionResponse start(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return projectService.start(currentUser(principal), id);
	}

	@PostMapping("/project-sessions/{id}/answer")
	public ProjectSessionResponse answer(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@Valid @RequestBody ProjectAnswerRequest request) {
		return projectService.answer(currentUser(principal), id, request);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

package com.javareview.today;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.today.TodayDtos.CreateManualTaskRequest;
import com.javareview.today.TodayDtos.RemoveReviewTasksRequest;
import com.javareview.today.TodayDtos.ReviewTaskResponse;
import com.javareview.today.TodayDtos.TodayPlanResponse;

@RestController
@RequestMapping("/api")
public class TodayController {

	private final TodayPlanService todayPlanService;
	private final AuthService authService;

	public TodayController(TodayPlanService todayPlanService, AuthService authService) {
		this.todayPlanService = todayPlanService;
		this.authService = authService;
	}

	@GetMapping("/today")
	public TodayPlanResponse getToday(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return todayPlanService.getToday(currentUser(principal));
	}

	@PostMapping("/today/generate")
	public TodayPlanResponse generateToday(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return todayPlanService.generateToday(currentUser(principal));
	}

	@PostMapping("/today/regenerate")
	public TodayPlanResponse regenerateToday(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return todayPlanService.regenerateToday(currentUser(principal));
	}

	@PostMapping("/today/manual-tasks")
	public ReviewTaskResponse createManualTask(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody CreateManualTaskRequest request) {
		return todayPlanService.createManualTask(currentUser(principal), request);
	}

	@DeleteMapping("/review-tasks/{id}")
	public TodayPlanResponse removeTask(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return todayPlanService.removeTask(currentUser(principal), id);
	}

	@PostMapping("/review-tasks/batch-remove")
	public TodayPlanResponse removeTasks(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody RemoveReviewTasksRequest request) {
		return todayPlanService.removeTasks(currentUser(principal), request);
	}

	@PatchMapping("/review-tasks/{id}/skip")
	public ReviewTaskResponse skipTask(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return todayPlanService.skipTask(currentUser(principal), id);
	}

	@PatchMapping("/review-tasks/{id}/unskip")
	public ReviewTaskResponse unskipTask(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return todayPlanService.unskipTask(currentUser(principal), id);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

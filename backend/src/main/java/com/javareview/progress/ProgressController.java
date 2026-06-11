package com.javareview.progress;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.progress.ProgressDtos.DomainProgressResponse;
import com.javareview.progress.ProgressDtos.DueReviewPointResponse;
import com.javareview.progress.ProgressDtos.ProgressOverviewResponse;
import com.javareview.progress.ProgressDtos.RecentSessionResponse;
import com.javareview.progress.ProgressDtos.ReviewPlanCalendarResponse;
import com.javareview.progress.ProgressDtos.TopicProgressResponse;
import com.javareview.progress.ProgressDtos.WeakPointResponse;

@RestController
@RequestMapping("/api/progress")
@Validated
public class ProgressController {

	private final ProgressService progressService;
	private final AuthService authService;

	public ProgressController(ProgressService progressService, AuthService authService) {
		this.progressService = progressService;
		this.authService = authService;
	}

	@GetMapping("/overview")
	public ProgressOverviewResponse overview(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.overview(currentUser(principal));
	}

	@GetMapping("/domains")
	public List<DomainProgressResponse> domains(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.domains(currentUser(principal));
	}

	@GetMapping("/topics")
	public List<TopicProgressResponse> topics(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@RequestParam(required = false) String status) {
		return progressService.topics(currentUser(principal), status);
	}

	@GetMapping("/weak-points")
	public List<WeakPointResponse> weakPoints(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.weakPoints(currentUser(principal));
	}

	@GetMapping("/due-review-points")
	public List<DueReviewPointResponse> dueReviewPoints(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.dueReviewPoints(currentUser(principal));
	}

	@GetMapping("/recent-sessions")
	public List<RecentSessionResponse> recentSessions(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.recentSessions(currentUser(principal));
	}

	@GetMapping("/review-plan-calendar")
	public ReviewPlanCalendarResponse reviewPlanCalendar(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate startDate,
			@RequestParam(defaultValue = "14")
			@Min(1)
			@Max(30)
			int days) {
		return progressService.reviewPlanCalendar(currentUser(principal), startDate, days);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

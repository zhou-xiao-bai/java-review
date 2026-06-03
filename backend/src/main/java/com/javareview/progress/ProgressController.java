package com.javareview.progress;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.javareview.progress.ProgressDtos.TopicProgressResponse;
import com.javareview.progress.ProgressDtos.WeakPointResponse;

@RestController
@RequestMapping("/api/progress")
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
	public List<DomainProgressResponse> domains() {
		return progressService.domains();
	}

	@GetMapping("/topics")
	public List<TopicProgressResponse> topics(@RequestParam(required = false) String status) {
		return progressService.topics(status);
	}

	@GetMapping("/weak-points")
	public List<WeakPointResponse> weakPoints() {
		return progressService.weakPoints();
	}

	@GetMapping("/due-review-points")
	public List<DueReviewPointResponse> dueReviewPoints() {
		return progressService.dueReviewPoints();
	}

	@GetMapping("/recent-sessions")
	public List<RecentSessionResponse> recentSessions(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return progressService.recentSessions(currentUser(principal));
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

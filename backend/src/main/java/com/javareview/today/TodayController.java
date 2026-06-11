package com.javareview.today;

import jakarta.validation.Valid;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.today.TodayDtos.TodayActionRequest;
import com.javareview.today.TodayDtos.TodayQueueResponse;

@RestController
@RequestMapping("/api")
public class TodayController {

	private final TodayQueueService todayQueueService;
	private final AuthService authService;

	public TodayController(TodayQueueService todayQueueService, AuthService authService) {
		this.todayQueueService = todayQueueService;
		this.authService = authService;
	}

	@GetMapping("/today/queue")
	public TodayQueueResponse getTodayQueue(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		return todayQueueService.getQueue(currentUser(principal));
	}

	@PostMapping("/today/actions")
	public TodayQueueResponse applyTodayAction(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody TodayActionRequest request) {
		return todayQueueService.applyAction(currentUser(principal), request);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

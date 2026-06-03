package com.javareview.reviewsession;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.reviewsession.ReviewSessionDtos.ClarifyRequest;
import com.javareview.reviewsession.ReviewSessionDtos.ReviewSessionResponse;
import com.javareview.reviewsession.ReviewSessionDtos.StartReviewSessionRequest;
import com.javareview.reviewsession.ReviewSessionDtos.SubmitAnswerRequest;

@RestController
@RequestMapping("/api/review-sessions")
public class ReviewSessionController {

	private final ReviewSessionService reviewSessionService;
	private final AuthService authService;

	public ReviewSessionController(ReviewSessionService reviewSessionService, AuthService authService) {
		this.reviewSessionService = reviewSessionService;
		this.authService = authService;
	}

	@PostMapping
	public ReviewSessionResponse start(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody StartReviewSessionRequest request) {
		return reviewSessionService.start(currentUser(principal), request);
	}

	@GetMapping("/{id}")
	public ReviewSessionResponse get(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return reviewSessionService.get(currentUser(principal), id);
	}

	@PostMapping("/{id}/answer")
	public ReviewSessionResponse answer(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@Valid @RequestBody SubmitAnswerRequest request) {
		return reviewSessionService.answer(currentUser(principal), id, request);
	}

	@PostMapping("/{id}/unknown")
	public ReviewSessionResponse unknown(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return reviewSessionService.unknown(currentUser(principal), id);
	}

	@PostMapping("/{id}/clarify")
	public ReviewSessionResponse clarify(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@RequestBody(required = false) ClarifyRequest request) {
		return reviewSessionService.clarify(currentUser(principal), id, request == null ? new ClarifyRequest(null) : request);
	}

	@PostMapping("/{id}/skip")
	public ReviewSessionResponse skip(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return reviewSessionService.skip(currentUser(principal), id);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

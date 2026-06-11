package com.javareview.reviewunit;

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
import com.javareview.reviewunit.ReviewUnitDtos.AdmitReviewUnitsRequest;
import com.javareview.reviewunit.ReviewUnitDtos.ReviewUnitsResponse;

@RestController
@RequestMapping("/api/topics/{topicId}/review-units")
public class ReviewUnitController {

	private final ReviewUnitService reviewUnitService;
	private final AuthService authService;

	public ReviewUnitController(ReviewUnitService reviewUnitService, AuthService authService) {
		this.reviewUnitService = reviewUnitService;
		this.authService = authService;
	}

	@GetMapping
	public ReviewUnitsResponse listTopicReviewUnits(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID topicId) {
		return reviewUnitService.listTopicReviewUnits(currentUser(principal), topicId);
	}

	@PostMapping("/admit")
	public ReviewUnitsResponse admitTopicReviewUnits(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID topicId,
			@Valid @RequestBody(required = false) AdmitReviewUnitsRequest request) {
		return reviewUnitService.admitTopicReviewUnits(currentUser(principal), topicId, request);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

package com.javareview.topic;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthService;
import com.javareview.auth.User;
import com.javareview.topic.TopicDtos.CreateTopicRequest;
import com.javareview.topic.TopicDtos.TopicSummaryResponse;
import com.javareview.topic.TopicDtos.TopicsResponse;
import com.javareview.topic.TopicDtos.UpdateTopicPlanningRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionsRequest;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

	private final TopicService topicService;
	private final AuthService authService;

	public TopicController(TopicService topicService, AuthService authService) {
		this.topicService = topicService;
		this.authService = authService;
	}

	@GetMapping
	public TopicsResponse listTopics(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@RequestParam(required = false) String search) {
		return topicService.listTopics(currentUser(principal), search);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TopicSummaryResponse createTopic(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody CreateTopicRequest request) {
		return topicService.createTopic(currentUser(principal), request);
	}

	@PatchMapping("/{id}/selection")
	public TopicSummaryResponse updateSelection(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTopicSelectionRequest request) {
		return topicService.updateSelection(currentUser(principal), id, request);
	}

	@PatchMapping("/selection")
	public TopicsResponse updateSelections(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@Valid @RequestBody UpdateTopicSelectionsRequest request) {
		return topicService.updateSelections(currentUser(principal), request);
	}

	@PatchMapping("/{id}/planning")
	public TopicSummaryResponse updatePlanning(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTopicPlanningRequest request) {
		return topicService.updatePlanning(currentUser(principal), id, request);
	}

	@PostMapping("/{id}/initialize-points")
	public TopicSummaryResponse initializePoints(
			@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
			@PathVariable UUID id) {
		return topicService.initializePoints(currentUser(principal), id);
	}

	private User currentUser(org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return authService.requireUserByIdentifier(principal.getUsername());
	}
}

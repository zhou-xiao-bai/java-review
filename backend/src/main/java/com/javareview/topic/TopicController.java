package com.javareview.topic;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

	public TopicController(TopicService topicService) {
		this.topicService = topicService;
	}

	@GetMapping
	public TopicsResponse listTopics(@RequestParam(required = false) String search) {
		return topicService.listTopics(search);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TopicSummaryResponse createTopic(@Valid @RequestBody CreateTopicRequest request) {
		return topicService.createTopic(request);
	}

	@PatchMapping("/{id}/selection")
	public TopicSummaryResponse updateSelection(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTopicSelectionRequest request) {
		return topicService.updateSelection(id, request);
	}

	@PatchMapping("/selection")
	public TopicsResponse updateSelections(@Valid @RequestBody UpdateTopicSelectionsRequest request) {
		return topicService.updateSelections(request);
	}

	@PatchMapping("/{id}/planning")
	public TopicSummaryResponse updatePlanning(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateTopicPlanningRequest request) {
		return topicService.updatePlanning(id, request);
	}

	@PostMapping("/{id}/initialize-points")
	public TopicSummaryResponse initializePoints(@PathVariable UUID id) {
		return topicService.initializePoints(id);
	}
}

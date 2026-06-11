package com.javareview.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.topic.TopicDtos.CreateTopicRequest;
import com.javareview.topic.TopicDtos.TopicSummaryResponse;
import com.javareview.topic.TopicDtos.TopicsResponse;
import com.javareview.topic.TopicDtos.UpdateTopicPlanningRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionRequest;
import com.javareview.topic.TopicDtos.UpdateTopicSelectionsRequest;

@ExtendWith(MockitoExtension.class)
class TopicServiceTests {

	@Mock
	private DomainRepository domainRepository;

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private ReviewPointRepository reviewPointRepository;

	private TopicService topicService;

	@BeforeEach
	void setUp() {
		topicService = new TopicService(
				domainRepository,
				topicRepository,
				reviewPointRepository);
	}

	@Test
	void selectingTopicInitializesReviewPoints() {
		Domain domain = new Domain(UUID.randomUUID(), "spring", "Spring", 40);
		Topic topic = new Topic(domain, "spring-transactions", "Spring 事务", TopicSource.BUILTIN, false);
		List<ReviewPoint> savedPoints = new ArrayList<>();
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.saveAll(any())).thenAnswer(invocation -> {
			Iterable<ReviewPoint> points = invocation.getArgument(0);
			points.forEach(savedPoints::add);
			return savedPoints;
		});
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(savedPoints);

		TopicSummaryResponse response = topicService.updateSelection(
				topic.getId(),
				new UpdateTopicSelectionRequest(true));

		assertThat(topic.isSelected()).isTrue();
		assertThat(response.selected()).isTrue();
		assertThat(response.reviewPointCount()).isEqualTo(6);
		assertThat(savedPoints)
				.extracting(ReviewPoint::getTitle)
				.contains("事务代理生效边界", "生产事务失效排查");
	}

	@Test
	void deselectingTopicKeepsExistingReviewPoints() {
		Domain domain = new Domain(UUID.randomUUID(), "spring", "Spring", 40);
		Topic topic = new Topic(domain, "spring-aop", "Spring AOP", TopicSource.BUILTIN, true);
		List<ReviewPoint> existingPoints = List.of(new ReviewPoint(
				topic,
				"Spring AOP 核心机制与调用链路",
				4,
				3,
				4,
				"next probe"));
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(existingPoints);

		TopicSummaryResponse response = topicService.updateSelection(
				topic.getId(),
				new UpdateTopicSelectionRequest(false));

		assertThat(topic.isSelected()).isFalse();
		assertThat(response.reviewPointCount()).isEqualTo(1);
		verify(reviewPointRepository, never()).saveAll(any());
	}

	@Test
	void creatingManualTopicSelectsAndInitializesIt() {
		Domain domain = new Domain(UUID.randomUUID(), "mysql", "MySQL", 80);
		List<ReviewPoint> savedPoints = new ArrayList<>();
		when(domainRepository.findById(domain.getId())).thenReturn(Optional.of(domain));
		when(topicRepository.existsByDomainIdAndTitleIgnoreCase(domain.getId(), "SQL 调优")).thenReturn(false);
		when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(reviewPointRepository.saveAll(any())).thenAnswer(invocation -> {
			Iterable<ReviewPoint> points = invocation.getArgument(0);
			points.forEach(savedPoints::add);
			return savedPoints;
		});
		when(reviewPointRepository.findByTopicId(any())).thenReturn(savedPoints);

		TopicSummaryResponse response = topicService.createTopic(new CreateTopicRequest(domain.getId(), " SQL 调优 "));

		assertThat(response.title()).isEqualTo("SQL 调优");
		assertThat(response.source()).isEqualTo("MANUAL");
		assertThat(response.selected()).isTrue();
		assertThat(response.relevanceTier()).isEqualTo("PROJECT");
		assertThat(response.planEnabled()).isTrue();
		assertThat(response.interviewValue()).isEqualTo(4);
		assertThat(response.reviewPointCount()).isEqualTo(5);
	}

	@Test
	void updatingPlanningControlsAutomaticPlanEligibility() {
		Domain domain = new Domain(UUID.randomUUID(), "java-foundation", "Java 基础", 10);
		Topic topic = new Topic(domain, "java-date-time", "java.time", TopicSource.BUILTIN, true);
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(List.of());

		TopicSummaryResponse response = topicService.updatePlanning(
				topic.getId(),
				new UpdateTopicPlanningRequest(RelevanceTier.SUPPLEMENT, false, 1, 1));

		assertThat(topic.isAutoPlannable()).isFalse();
		assertThat(response.relevanceTier()).isEqualTo("SUPPLEMENT");
		assertThat(response.planEnabled()).isFalse();
		assertThat(response.interviewValue()).isEqualTo(1);
		assertThat(response.newExpansionLimit()).isEqualTo(1);
	}

	@Test
	void initializingTopicAddsOnlyMissingBuiltInReviewPoints() {
		Domain domain = new Domain(UUID.randomUUID(), "spring", "Spring", 40);
		Topic topic = new Topic(domain, "spring-transactions", "Spring 事务", TopicSource.BUILTIN, false);
		List<ReviewPoint> savedPoints = new ArrayList<>(List.of(new ReviewPoint(
				topic,
				"事务代理生效边界",
				5,
				4,
				5,
				"next probe")));
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(savedPoints);
		when(reviewPointRepository.saveAll(any())).thenAnswer(invocation -> {
			Iterable<ReviewPoint> points = invocation.getArgument(0);
			points.forEach(savedPoints::add);
			return savedPoints;
		});

		TopicSummaryResponse response = topicService.initializePoints(topic.getId());

		assertThat(response.reviewPointCount()).isEqualTo(6);
		assertThat(savedPoints)
				.extracting(ReviewPoint::getTitle)
				.containsOnlyOnce("事务代理生效边界")
				.contains("传播行为与嵌套调用", "生产事务失效排查");
	}

	@Test
	void bulkSelectingTopicsInitializesSelectedTopics() {
		Domain domain = new Domain(UUID.randomUUID(), "redis", "Redis", 90);
		Topic cacheTopic = new Topic(domain, "redis-cache-consistency", "缓存一致性", TopicSource.BUILTIN, false);
		Topic streamTopic = new Topic(domain, "redis-streams", "Stream", TopicSource.BUILTIN, false);
		List<ReviewPoint> savedPoints = new ArrayList<>();
		when(topicRepository.findAllById(List.of(cacheTopic.getId(), streamTopic.getId())))
				.thenReturn(List.of(cacheTopic, streamTopic));
		when(reviewPointRepository.findByTopicId(any())).thenAnswer(invocation -> {
			UUID topicId = invocation.getArgument(0);
			return savedPoints.stream()
					.filter(point -> point.getTopic().getId().equals(topicId))
					.toList();
		});
		when(domainRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(domain));
		when(topicRepository.findAllWithDomain()).thenReturn(List.of(cacheTopic, streamTopic));
		when(reviewPointRepository.saveAll(any())).thenAnswer(invocation -> {
			Iterable<ReviewPoint> points = invocation.getArgument(0);
			points.forEach(savedPoints::add);
			return savedPoints;
		});
		when(reviewPointRepository.findByTopicIdIn(List.of(cacheTopic.getId(), streamTopic.getId())))
				.thenReturn(savedPoints);

		TopicsResponse response = topicService.updateSelections(new UpdateTopicSelectionsRequest(
				List.of(cacheTopic.getId(), streamTopic.getId()),
				true));

		assertThat(cacheTopic.isSelected()).isTrue();
		assertThat(streamTopic.isSelected()).isTrue();
		assertThat(response.totals().selectedTopicCount()).isEqualTo(2);
		assertThat(savedPoints)
				.extracting(ReviewPoint::getTitle)
				.contains("缓存更新策略选择", "Stream 生产问题排查");
	}
}

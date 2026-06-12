package com.javareview.reviewunit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicRepository;
import com.javareview.reviewunit.ReviewUnitDtos.AdmitReviewUnitsRequest;
import com.javareview.reviewunit.ReviewUnitDtos.ReviewUnitSummaryResponse;
import com.javareview.reviewunit.ReviewUnitDtos.ReviewUnitsResponse;

@Service
public class ReviewUnitService {

	private final TopicRepository topicRepository;
	private final ReviewPointRepository reviewPointRepository;
	private final QuestionVariantRepository questionVariantRepository;
	private final UserReviewUnitStateRepository stateRepository;
	private final Clock clock;

	public ReviewUnitService(
			TopicRepository topicRepository,
			ReviewPointRepository reviewPointRepository,
			QuestionVariantRepository questionVariantRepository,
			UserReviewUnitStateRepository stateRepository,
			Clock clock) {
		this.topicRepository = topicRepository;
		this.reviewPointRepository = reviewPointRepository;
		this.questionVariantRepository = questionVariantRepository;
		this.stateRepository = stateRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReviewUnitsResponse listTopicReviewUnits(User user, UUID topicId) {
		Topic topic = requireTopic(topicId);
		List<ReviewPoint> units = sortedUnits(reviewPointRepository.findByTopicId(topic.getId()));
		return toResponse(user, topic, units);
	}

	@Transactional
	public ReviewUnitsResponse admitTopicReviewUnits(User user, UUID topicId, AdmitReviewUnitsRequest request) {
		Topic topic = requireTopic(topicId);
		List<ReviewPoint> topicUnits = sortedUnits(reviewPointRepository.findByTopicId(topic.getId()));
		List<ReviewPoint> unitsToAdmit = selectUnitsToAdmit(topicUnits, request == null ? null : request.reviewUnitIds());
		if (!unitsToAdmit.isEmpty()) {
			Map<UUID, UserReviewUnitState> existingStates = statesByUnitId(user.getId(), unitsToAdmit);
			Instant now = Instant.now(clock);
			List<UserReviewUnitState> newStates = unitsToAdmit.stream()
					.filter(unit -> !existingStates.containsKey(unit.getId()))
					.map(unit -> new UserReviewUnitState(user, unit, now))
					.toList();
			if (!newStates.isEmpty()) {
				stateRepository.saveAll(newStates);
			}
		}
		return toResponse(user, topic, topicUnits);
	}

	private Topic requireTopic(UUID topicId) {
		return topicRepository.findById(topicId)
				.orElseThrow(() -> new ResourceNotFoundException("Topic not found."));
	}

	private List<ReviewPoint> sortedUnits(List<ReviewPoint> units) {
		return units.stream()
				.sorted(Comparator
						.comparing(ReviewPoint::getImportance, Comparator.reverseOrder())
						.thenComparing(ReviewPoint::getInterviewFrequency, Comparator.reverseOrder())
						.thenComparing(ReviewPoint::getDifficulty, Comparator.reverseOrder())
						.thenComparing(ReviewPoint::getTitle))
				.toList();
	}

	private List<ReviewPoint> selectUnitsToAdmit(List<ReviewPoint> topicUnits, List<UUID> requestedUnitIds) {
		if (requestedUnitIds == null || requestedUnitIds.isEmpty()) {
			return topicUnits;
		}
		List<UUID> uniqueUnitIds = requestedUnitIds.stream()
				.collect(Collectors.collectingAndThen(
						Collectors.toCollection(LinkedHashSet::new),
						ArrayList::new));
		Map<UUID, ReviewPoint> unitsById = topicUnits.stream()
				.collect(Collectors.toMap(ReviewPoint::getId, Function.identity()));
		List<ReviewPoint> selectedUnits = new ArrayList<>();
		for (UUID unitId : uniqueUnitIds) {
			ReviewPoint unit = unitsById.get(unitId);
			if (unit == null) {
				throw new ResourceNotFoundException("Review unit not found in topic.");
			}
			selectedUnits.add(unit);
		}
		return selectedUnits;
	}

	private ReviewUnitsResponse toResponse(User user, Topic topic, List<ReviewPoint> units) {
		Map<UUID, UserReviewUnitState> statesByUnitId = statesByUnitId(user.getId(), units);
		Map<UUID, Long> variantCountsByUnitId = variantCountsByUnitId(units);
		List<ReviewUnitSummaryResponse> unitResponses = units.stream()
				.map(unit -> toUnitResponse(unit, statesByUnitId.get(unit.getId()), variantCountsByUnitId.getOrDefault(unit.getId(), 0L)))
				.toList();
		return new ReviewUnitsResponse(
				topic.getId(),
				topic.getTitle(),
				topic.getDomain().getName(),
				units.size(),
				unitResponses.stream().mapToLong(ReviewUnitSummaryResponse::questionVariantCount).sum(),
				unitResponses.stream().filter(unit -> unit.stateId() != null).count(),
				unitResponses.stream().filter(unit -> unit.stateStatus() != null
						&& unit.stateStatus().equals(UserReviewUnitStatus.PENDING_FIRST_REVIEW.name())).count(),
				unitResponses.stream().filter(unit -> unit.stateStatus() != null
						&& unit.stateStatus().equals(UserReviewUnitStatus.ACTIVE.name())).count(),
				unitResponses);
	}

	private Map<UUID, UserReviewUnitState> statesByUnitId(UUID userId, Collection<ReviewPoint> units) {
		if (units.isEmpty()) {
			return Map.of();
		}
		List<UUID> unitIds = units.stream().map(ReviewPoint::getId).toList();
		return stateRepository.findByUserIdAndReviewUnitIdIn(userId, unitIds)
				.stream()
				.collect(Collectors.toMap(state -> state.getReviewUnit().getId(), Function.identity()));
	}

	private Map<UUID, Long> variantCountsByUnitId(Collection<ReviewPoint> units) {
		if (units.isEmpty()) {
			return Map.of();
		}
		List<UUID> unitIds = units.stream().map(ReviewPoint::getId).toList();
		return questionVariantRepository.countEnabledByReviewUnitIds(unitIds)
				.stream()
				.collect(Collectors.toMap(
						QuestionVariantRepository.ReviewUnitVariantCount::getReviewUnitId,
						QuestionVariantRepository.ReviewUnitVariantCount::getVariantCount));
	}

	private ReviewUnitSummaryResponse toUnitResponse(ReviewPoint unit, UserReviewUnitState state, long questionVariantCount) {
		return new ReviewUnitSummaryResponse(
				unit.getId(),
				unit.getTitle(),
				unit.getImportance(),
				unit.getDifficulty(),
				unit.getInterviewFrequency(),
				questionVariantCount,
				unit.getAutoPlanTier().name(),
				unit.getMastery(),
				unit.getStatus().name(),
				state == null ? null : state.getId(),
				state == null ? null : state.getStatus().name(),
				state == null ? null : state.getAdmittedAt(),
				state == null ? null : state.getFirstReviewedAt(),
				state == null ? null : state.getLastReviewedAt(),
				state == null ? null : state.getNextReviewAt(),
				state == null || state.getLastResult() == null ? null : state.getLastResult().name(),
				state == null ? 0 : state.getConsecutiveSuccessCount(),
				state == null ? 0 : state.getConsecutiveFailureCount(),
				unit.getWeakPoints(),
				unit.getNextProbe());
	}
}

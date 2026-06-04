package com.javareview.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.progress.ProgressDtos.DomainProgressResponse;
import com.javareview.progress.ProgressDtos.DueReviewPointResponse;
import com.javareview.progress.ProgressDtos.ProgressOverviewResponse;
import com.javareview.progress.ProgressDtos.RecentSessionResponse;
import com.javareview.progress.ProgressDtos.TopicProgressResponse;
import com.javareview.progress.ProgressDtos.WeakPointResponse;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.reviewpoint.ReviewWeaknessEvent;
import com.javareview.reviewpoint.ReviewWeaknessEventRepository;
import com.javareview.reviewpoint.WeaknessEventStatus;
import com.javareview.reviewsession.ReviewSession;
import com.javareview.reviewsession.ReviewSessionRepository;
import com.javareview.reviewsession.ReviewSessionStatus;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicRepository;

@Service
public class ProgressService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final TopicRepository topicRepository;
	private final ReviewPointRepository reviewPointRepository;
	private final ReviewWeaknessEventRepository weaknessEventRepository;
	private final ReviewSessionRepository reviewSessionRepository;
	private final Clock clock;

	public ProgressService(
			TopicRepository topicRepository,
			ReviewPointRepository reviewPointRepository,
			ReviewWeaknessEventRepository weaknessEventRepository,
			ReviewSessionRepository reviewSessionRepository,
			Clock clock) {
		this.topicRepository = topicRepository;
		this.reviewPointRepository = reviewPointRepository;
		this.weaknessEventRepository = weaknessEventRepository;
		this.reviewSessionRepository = reviewSessionRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ProgressOverviewResponse overview(User user) {
		ProgressData data = loadData();
		return new ProgressOverviewResponse(
				averageMastery(data.points()),
				data.topics().size(),
				data.points().size(),
				data.points().stream().filter(ProgressService::unstable).count(),
				duePoints(data.points()).size(),
				reviewSessionRepository.countByUserIdAndStatus(user.getId(), ReviewSessionStatus.EVALUATED),
				openWeaknessEvents(data.events()).size(),
				data.points().stream().filter(ProgressService::highRisk).count(),
				data.topics().stream().filter(Topic::isAutoPlannable).count());
	}

	@Transactional(readOnly = true)
	public List<DomainProgressResponse> domains() {
		ProgressData data = loadData();
		Map<UUID, List<ReviewPoint>> pointsByDomain = data.points().stream()
				.collect(Collectors.groupingBy(point -> point.getTopic().getDomain().getId(), HashMap::new, Collectors.toList()));
		return data.topics().stream()
				.collect(Collectors.groupingBy(topic -> topic.getDomain().getId(), HashMap::new, Collectors.toList()))
				.values()
				.stream()
				.sorted(Comparator.comparing(topics -> topics.get(0).getDomain().getSortOrder()))
				.map(topics -> {
					Topic first = topics.get(0);
					List<ReviewPoint> points = pointsByDomain.getOrDefault(first.getDomain().getId(), List.of());
					return new DomainProgressResponse(
							first.getDomain().getId(),
							first.getDomain().getName(),
							topics.size(),
							points.size(),
							averageMastery(points),
							points.stream().filter(ProgressService::unstable).count(),
							points.stream().filter(point -> point.getStatus() == ReviewPointStatus.DUE || point.getStatus() == ReviewPointStatus.FIRST_PASS).count(),
							points.stream().filter(ProgressService::stable).count(),
							points.stream().filter(point -> point.getStatus() == ReviewPointStatus.UNCOVERED).count(),
							openWeaknessEvents(data.events(), points).size());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public List<TopicProgressResponse> topics(String status) {
		ProgressData data = loadData();
		Map<UUID, List<ReviewPoint>> pointsByTopic = data.points().stream()
				.collect(Collectors.groupingBy(point -> point.getTopic().getId(), HashMap::new, Collectors.toList()));
		Map<UUID, List<ReviewWeaknessEvent>> eventsByTopic = data.events().stream()
				.collect(Collectors.groupingBy(event -> event.getReviewPoint().getTopic().getId(), HashMap::new, Collectors.toList()));
		String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
		return data.topics().stream()
				.map(topic -> topicResponse(
						topic,
						pointsByTopic.getOrDefault(topic.getId(), List.of()),
						eventsByTopic.getOrDefault(topic.getId(), List.of())))
				.filter(topic -> normalizedStatus == null || normalizedStatus.isBlank() || topic.status().equalsIgnoreCase(normalizedStatus))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WeakPointResponse> weakPoints() {
		ProgressData data = loadData();
		List<WeakPointResponse> eventResponses = openWeaknessEvents(data.events()).stream()
				.sorted((left, right) -> {
					int result = Integer.compare(right.getSeverity(), left.getSeverity());
					if (result != 0) {
						return result;
					}
					Instant leftCreatedAt = left.getCreatedAt() == null ? Instant.EPOCH : left.getCreatedAt();
					Instant rightCreatedAt = right.getCreatedAt() == null ? Instant.EPOCH : right.getCreatedAt();
					return rightCreatedAt.compareTo(leftCreatedAt);
				})
				.map(event -> new WeakPointResponse(
						event.getLabel(),
						event.getCategory(),
						event.getEvidence(),
						event.getSeverity(),
						event.getStatus().name().toLowerCase(Locale.ROOT),
						event.getReviewPoint().getTopic().getTitle(),
						event.getReviewPoint().getTitle(),
						event.getReviewPoint().getMastery(),
						event.getCreatedAt()))
				.limit(20)
				.toList();
		if (!eventResponses.isEmpty()) {
			return eventResponses;
		}
		return data.points().stream()
				.flatMap(point -> point.getWeakPoints().stream()
						.map(weakPoint -> new WeakPointResponse(
								weakPoint,
								"legacy",
								null,
								3,
								"open",
								point.getTopic().getTitle(),
								point.getTitle(),
								point.getMastery(),
								point.getLastReviewedAt())))
				.limit(20)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<DueReviewPointResponse> dueReviewPoints() {
		return duePoints(loadData().points()).stream()
				.map(point -> new DueReviewPointResponse(
						point.getId(),
						point.getTopic().getTitle(),
						point.getTitle(),
						point.getStatus().name().toLowerCase(Locale.ROOT),
						point.getMastery(),
						point.getNextReviewAt(),
						dueReason(point),
						point.getNextProbe()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<RecentSessionResponse> recentSessions(User user) {
		return reviewSessionRepository.findRecentByUserId(user.getId()).stream()
				.limit(20)
				.map(this::recentSessionResponse)
				.toList();
	}

	private ProgressData loadData() {
		List<Topic> topics = topicRepository.findAllWithDomain().stream()
				.filter(Topic::isSelected)
				.toList();
		List<ReviewPoint> points = topics.isEmpty()
				? List.of()
				: reviewPointRepository.findByTopicIdIn(topics.stream().map(Topic::getId).toList());
		List<ReviewWeaknessEvent> events = points.isEmpty()
				? List.of()
				: weaknessEventRepository.findByReviewPoint_IdIn(points.stream().map(ReviewPoint::getId).toList());
		return new ProgressData(topics, points, events);
	}

	private TopicProgressResponse topicResponse(Topic topic, List<ReviewPoint> points, List<ReviewWeaknessEvent> events) {
		ReviewPointStatus status = topicStatus(points);
		List<ReviewWeaknessEvent> topicEvents = openWeaknessEvents(events);
		return new TopicProgressResponse(
				topic.getId(),
				topic.getTitle(),
				topic.getDomain().getName(),
				status.name().toLowerCase(Locale.ROOT),
				topic.getRelevanceTier().name(),
				topic.isPlanEnabled(),
				topic.getInterviewValue(),
				points.size(),
				points.stream().filter(ProgressService::unstable).count(),
				points.stream().filter(point -> point.getStatus() == ReviewPointStatus.DUE || point.getStatus() == ReviewPointStatus.FIRST_PASS).count(),
				points.stream().filter(ProgressService::stable).count(),
				points.stream().filter(point -> point.getStatus() == ReviewPointStatus.UNCOVERED).count(),
				topicEvents.size(),
				averageMastery(points),
				points.stream().map(ReviewPoint::getNextReviewAt).filter(value -> value != null).min(Comparator.naturalOrder()).orElse(null),
				topicEvents.isEmpty()
						? points.stream().flatMap(point -> point.getWeakPoints().stream()).distinct().limit(3).toList()
						: topicEvents.stream().map(ReviewWeaknessEvent::getLabel).distinct().limit(3).toList());
	}

	private RecentSessionResponse recentSessionResponse(ReviewSession session) {
		ReviewPoint point = session.getTask().getReviewPoint();
		return new RecentSessionResponse(
				session.getId(),
				point == null ? null : point.getTopic().getTitle(),
				point == null ? null : point.getTitle(),
				session.getTask().getManualPrompt(),
				session.getStatus().name().toLowerCase(Locale.ROOT),
				session.getFinalScore(),
				session.getStartedAt(),
				session.getEndedAt());
	}

	private static BigDecimal averageMastery(Collection<ReviewPoint> points) {
		if (points.isEmpty()) {
			return ZERO;
		}
		return points.stream()
				.map(ReviewPoint::getMastery)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);
	}

	private static ReviewPointStatus topicStatus(List<ReviewPoint> points) {
		if (points.isEmpty()) {
			return ReviewPointStatus.UNCOVERED;
		}
		if (points.stream().allMatch(point -> point.getStatus() == ReviewPointStatus.UNCOVERED)) {
			return ReviewPointStatus.UNCOVERED;
		}
		if (points.stream().anyMatch(point -> point.getStatus() == ReviewPointStatus.UNSTABLE)) {
			return ReviewPointStatus.UNSTABLE;
		}
		if (points.stream().anyMatch(point -> point.getStatus() == ReviewPointStatus.DUE || point.getStatus() == ReviewPointStatus.FIRST_PASS)) {
			return ReviewPointStatus.DUE;
		}
		if (points.stream().allMatch(point -> point.getStatus() == ReviewPointStatus.LONG_TERM)) {
			return ReviewPointStatus.LONG_TERM;
		}
		return ReviewPointStatus.STABLE;
	}

	private List<ReviewPoint> duePoints(List<ReviewPoint> points) {
		Instant now = Instant.now(clock);
		return points.stream()
				.filter(point -> point.getNextReviewAt() != null && !point.getNextReviewAt().isAfter(now))
				.sorted(Comparator.comparing(ReviewPoint::getNextReviewAt))
				.toList();
	}

	private static boolean unstable(ReviewPoint point) {
		return point.getStatus() == ReviewPointStatus.UNSTABLE || point.getStatus() == ReviewPointStatus.DUE;
	}

	private static boolean stable(ReviewPoint point) {
		return point.getStatus() == ReviewPointStatus.STABLE || point.getStatus() == ReviewPointStatus.LONG_TERM;
	}

	private static boolean highRisk(ReviewPoint point) {
		return point.getStatus() == ReviewPointStatus.UNSTABLE
				|| point.getWrongCount() >= 2
				|| (point.getStatus() != ReviewPointStatus.UNCOVERED
						&& point.getMastery().compareTo(BigDecimal.valueOf(2.5)) < 0);
	}

	private static List<ReviewWeaknessEvent> openWeaknessEvents(List<ReviewWeaknessEvent> events) {
		return events.stream()
				.filter(event -> event.getStatus() == WeaknessEventStatus.OPEN || event.getStatus() == WeaknessEventStatus.IMPROVING)
				.toList();
	}

	private static List<ReviewWeaknessEvent> openWeaknessEvents(List<ReviewWeaknessEvent> events, List<ReviewPoint> points) {
		List<UUID> pointIds = points.stream().map(ReviewPoint::getId).toList();
		return openWeaknessEvents(events).stream()
				.filter(event -> pointIds.contains(event.getReviewPoint().getId()))
				.toList();
	}

	private String dueReason(ReviewPoint point) {
		if (point.getStatus() == ReviewPointStatus.UNSTABLE) {
			return "上次评价不稳定";
		}
		if (point.getWrongCount() > 0) {
			return "历史答错 " + point.getWrongCount() + " 次";
		}
		if (point.getNextReviewAt() != null && !point.getNextReviewAt().isAfter(Instant.now(clock))) {
			return "到期复验";
		}
		return "需要复验";
	}

	private record ProgressData(List<Topic> topics, List<ReviewPoint> points, List<ReviewWeaknessEvent> events) {
	}
}

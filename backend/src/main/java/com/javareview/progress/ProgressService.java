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
	private final ReviewSessionRepository reviewSessionRepository;
	private final Clock clock;

	public ProgressService(
			TopicRepository topicRepository,
			ReviewPointRepository reviewPointRepository,
			ReviewSessionRepository reviewSessionRepository,
			Clock clock) {
		this.topicRepository = topicRepository;
		this.reviewPointRepository = reviewPointRepository;
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
				reviewSessionRepository.countByUserIdAndStatus(user.getId(), ReviewSessionStatus.EVALUATED));
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
							points.stream().filter(ProgressService::unstable).count());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public List<TopicProgressResponse> topics(String status) {
		ProgressData data = loadData();
		Map<UUID, List<ReviewPoint>> pointsByTopic = data.points().stream()
				.collect(Collectors.groupingBy(point -> point.getTopic().getId(), HashMap::new, Collectors.toList()));
		String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
		return data.topics().stream()
				.map(topic -> topicResponse(topic, pointsByTopic.getOrDefault(topic.getId(), List.of())))
				.filter(topic -> normalizedStatus == null || normalizedStatus.isBlank() || topic.status().equalsIgnoreCase(normalizedStatus))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WeakPointResponse> weakPoints() {
		return loadData().points().stream()
				.flatMap(point -> point.getWeakPoints().stream()
						.map(weakPoint -> new WeakPointResponse(
								weakPoint,
								point.getTopic().getTitle(),
								point.getTitle(),
								point.getMastery())))
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
						point.getNextReviewAt()))
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
		return new ProgressData(topics, points);
	}

	private TopicProgressResponse topicResponse(Topic topic, List<ReviewPoint> points) {
		ReviewPointStatus status = topicStatus(points);
		return new TopicProgressResponse(
				topic.getId(),
				topic.getTitle(),
				topic.getDomain().getName(),
				status.name().toLowerCase(Locale.ROOT),
				points.size(),
				points.stream().filter(ProgressService::unstable).count(),
				averageMastery(points),
				points.stream().map(ReviewPoint::getNextReviewAt).filter(value -> value != null).min(Comparator.naturalOrder()).orElse(null),
				points.stream().flatMap(point -> point.getWeakPoints().stream()).distinct().limit(3).toList());
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

	private record ProgressData(List<Topic> topics, List<ReviewPoint> points) {
	}
}

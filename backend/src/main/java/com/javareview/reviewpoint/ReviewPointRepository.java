package com.javareview.reviewpoint;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewPointRepository extends JpaRepository<ReviewPoint, UUID> {

	boolean existsByTopicId(UUID topicId);

	List<ReviewPoint> findByTopicId(UUID topicId);

	List<ReviewPoint> findByTopicIdIn(Collection<UUID> topicIds);

	@Query("""
			select point
			from ReviewPoint point
			join fetch point.topic topic
			join fetch topic.domain domain
			where topic.selected = true
			and topic.planEnabled = true
			and topic.relevanceTier in (com.javareview.topic.RelevanceTier.CORE, com.javareview.topic.RelevanceTier.PROJECT)
			and point.nextReviewAt is not null
			and point.nextReviewAt <= :endOfRange
			""")
	List<ReviewPoint> findReviewPlanCalendarPoints(@Param("endOfRange") Instant endOfRange);

	@Query("""
			select point
			from ReviewPoint point
			join fetch point.topic topic
			join fetch topic.domain domain
			where (
				point.reviewCount > 0
				or (
					topic.selected = true
					and topic.planEnabled = true
					and topic.relevanceTier in (com.javareview.topic.RelevanceTier.CORE, com.javareview.topic.RelevanceTier.PROJECT)
				)
			)
			and point.nextReviewAt is not null
			and point.nextReviewAt <= :endOfRange
			""")
	List<ReviewPoint> findReviewPlanCalendarPointsIncludingReviewedOutsideScope(
			@Param("endOfRange") Instant endOfRange);

}

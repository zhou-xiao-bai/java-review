package com.javareview.reviewsession;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, UUID> {

	@Query("""
			select session
			from ReviewSession session
			join fetch session.task task
			left join fetch task.reviewPoint point
			left join fetch point.topic topic
			left join fetch topic.domain domain
			where session.id = :id
			and session.user.id = :userId
			""")
	Optional<ReviewSession> findByIdAndUserIdWithTask(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	@Query("""
			select session
			from ReviewSession session
			join fetch session.task task
			left join fetch task.reviewPoint point
			left join fetch point.topic topic
			left join fetch topic.domain domain
			where session.user.id = :userId
			order by session.startedAt desc
			""")
	List<ReviewSession> findRecentByUserId(@Param("userId") UUID userId);

	long countByUserIdAndStatus(UUID userId, ReviewSessionStatus status);
}

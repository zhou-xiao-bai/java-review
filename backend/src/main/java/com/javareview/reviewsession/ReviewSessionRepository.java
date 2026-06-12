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
			join fetch session.reviewUnitState state
			join fetch state.reviewUnit point
			join fetch point.topic topic
			join fetch topic.domain domain
			left join fetch session.questionVariant variant
			where session.id = :id
			and session.user.id = :userId
			""")
	Optional<ReviewSession> findByIdAndUserIdWithUnit(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	@Query("""
			select session
			from ReviewSession session
			join fetch session.reviewUnitState state
			join fetch state.reviewUnit point
			join fetch point.topic topic
			join fetch topic.domain domain
			left join fetch session.questionVariant variant
			where state.id = :stateId
			and session.user.id = :userId
			and session.status = com.javareview.reviewsession.ReviewSessionStatus.ACTIVE
			order by session.startedAt desc
			""")
	List<ReviewSession> findActiveByStateIdAndUserId(
			@Param("stateId") UUID stateId,
			@Param("userId") UUID userId);

	@Query("""
			select session
			from ReviewSession session
			join fetch session.reviewUnitState state
			join fetch state.reviewUnit point
			join fetch point.topic topic
			join fetch topic.domain domain
			left join fetch session.questionVariant variant
			where session.user.id = :userId
			order by session.startedAt desc
			""")
	List<ReviewSession> findRecentByUserId(@Param("userId") UUID userId);

	long countByUserIdAndStatus(UUID userId, ReviewSessionStatus status);
}

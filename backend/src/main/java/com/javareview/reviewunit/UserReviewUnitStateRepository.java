package com.javareview.reviewunit;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReviewUnitStateRepository extends JpaRepository<UserReviewUnitState, UUID> {

	@Query("""
			select state
			from UserReviewUnitState state
			join fetch state.reviewUnit unit
			join fetch unit.topic topic
			join fetch topic.domain domain
			where state.user.id = :userId
			and state.status in :statuses
			""")
	List<UserReviewUnitState> findQueueCandidates(
			@Param("userId") UUID userId,
			@Param("statuses") Collection<UserReviewUnitStatus> statuses);

	Optional<UserReviewUnitState> findByUserIdAndReviewUnitId(UUID userId, UUID reviewUnitId);

	@Query("""
			select state
			from UserReviewUnitState state
			join fetch state.reviewUnit unit
			join fetch unit.topic topic
			join fetch topic.domain domain
			where state.user.id = :userId
			and unit.id = :reviewUnitId
			""")
	Optional<UserReviewUnitState> findByUserIdAndReviewUnitIdWithUnit(
			@Param("userId") UUID userId,
			@Param("reviewUnitId") UUID reviewUnitId);

	List<UserReviewUnitState> findByUserIdAndReviewUnitIdIn(UUID userId, Collection<UUID> reviewUnitIds);

	@Modifying
	@Query("""
			delete from UserReviewUnitState state
			where state.user.id = :userId
			and state.reviewUnit.id in :reviewUnitIds
			and state.status = com.javareview.reviewunit.UserReviewUnitStatus.PENDING_FIRST_REVIEW
			and state.firstReviewedAt is null
			""")
	int deletePendingUnreviewedByUserIdAndReviewUnitIdIn(
			@Param("userId") UUID userId,
			@Param("reviewUnitIds") Collection<UUID> reviewUnitIds);

	@Query("""
			select state
			from UserReviewUnitState state
			join fetch state.reviewUnit unit
			join fetch unit.topic topic
			join fetch topic.domain domain
			where state.user.id = :userId
			and state.status in :statuses
			""")
	List<UserReviewUnitState> findByUserIdAndStatusInWithUnit(
			@Param("userId") UUID userId,
			@Param("statuses") Collection<UserReviewUnitStatus> statuses);

	@Query("""
			select state
			from UserReviewUnitState state
			join fetch state.reviewUnit unit
			join fetch unit.topic topic
			join fetch topic.domain domain
			where state.id = :id
			and state.user.id = :userId
			""")
	Optional<UserReviewUnitState> findByIdAndUserIdWithUnit(
			@Param("id") UUID id,
			@Param("userId") UUID userId);

	@Query("""
			select state
			from UserReviewUnitState state
			join fetch state.reviewUnit unit
			join fetch unit.topic topic
			join fetch topic.domain domain
			where state.user.id = :userId
			and state.status in :statuses
			and (
				state.status = com.javareview.reviewunit.UserReviewUnitStatus.PENDING_FIRST_REVIEW
				or (state.nextReviewAt is not null and state.nextReviewAt <= :endAt)
			)
			""")
	List<UserReviewUnitState> findCalendarCandidates(
			@Param("userId") UUID userId,
			@Param("statuses") Collection<UserReviewUnitStatus> statuses,
			@Param("endAt") Instant endAt);
}

package com.javareview.reviewunit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, UUID> {

	@Query("""
			select variant.id
			from ReviewAttempt attempt
			join attempt.questionVariant variant
			where attempt.user.id = :userId
			and attempt.reviewUnit.id = :reviewUnitId
			order by attempt.attemptedAt desc
			""")
	List<UUID> findRecentQuestionVariantIds(
			@Param("userId") UUID userId,
			@Param("reviewUnitId") UUID reviewUnitId,
			Pageable pageable);
}

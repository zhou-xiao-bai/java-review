package com.javareview.reviewunit;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodayReviewActionRepository extends JpaRepository<TodayReviewAction, UUID> {

	@Query("""
			select action
			from TodayReviewAction action
			join fetch action.reviewUnit unit
			where action.user.id = :userId
			and action.actionDate = :actionDate
			order by action.createdAt asc, action.id asc
			""")
	List<TodayReviewAction> findByUserIdAndActionDate(
			@Param("userId") UUID userId,
			@Param("actionDate") LocalDate actionDate);
}

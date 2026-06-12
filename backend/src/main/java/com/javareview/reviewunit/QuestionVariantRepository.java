package com.javareview.reviewunit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionVariantRepository extends JpaRepository<QuestionVariant, UUID> {

	List<QuestionVariant> findByReviewUnitIdAndEnabledTrue(UUID reviewUnitId);

	@Query("""
			select variant.reviewUnit.id as reviewUnitId, count(variant) as variantCount
			from QuestionVariant variant
			where variant.reviewUnit.id in :reviewUnitIds
			and variant.enabled = true
			group by variant.reviewUnit.id
			""")
	List<ReviewUnitVariantCount> countEnabledByReviewUnitIds(@Param("reviewUnitIds") Collection<UUID> reviewUnitIds);

	interface ReviewUnitVariantCount {

		UUID getReviewUnitId();

		long getVariantCount();
	}
}

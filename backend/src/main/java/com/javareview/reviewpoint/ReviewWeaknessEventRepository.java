package com.javareview.reviewpoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewWeaknessEventRepository extends JpaRepository<ReviewWeaknessEvent, UUID> {

	List<ReviewWeaknessEvent> findByReviewPoint_IdIn(Collection<UUID> reviewPointIds);
}

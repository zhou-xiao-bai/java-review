package com.javareview.reviewpoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPointRepository extends JpaRepository<ReviewPoint, UUID> {

	boolean existsByTopicId(UUID topicId);

	List<ReviewPoint> findByTopicId(UUID topicId);

	List<ReviewPoint> findByTopicIdIn(Collection<UUID> topicIds);
}

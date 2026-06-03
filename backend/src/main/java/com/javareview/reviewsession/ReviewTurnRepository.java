package com.javareview.reviewsession;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTurnRepository extends JpaRepository<ReviewTurn, UUID> {

	List<ReviewTurn> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}

package com.javareview.reviewunit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, UUID> {
}

package com.javareview.reviewunit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.javareview.auth.User;
import com.javareview.reviewpoint.ReviewPoint;

@Entity
@Table(name = "user_review_unit_states")
public class UserReviewUnitState {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_unit_id", nullable = false)
	private ReviewPoint reviewUnit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserReviewUnitStatus status;

	@Column(name = "admitted_at", nullable = false)
	private Instant admittedAt;

	@Column(name = "first_reviewed_at")
	private Instant firstReviewedAt;

	@Column(name = "last_reviewed_at")
	private Instant lastReviewedAt;

	@Column(name = "next_review_at")
	private Instant nextReviewAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "last_result", length = 32)
	private ReviewAttemptResult lastResult;

	@Column(name = "consecutive_success_count", nullable = false)
	private int consecutiveSuccessCount;

	@Column(name = "consecutive_failure_count", nullable = false)
	private int consecutiveFailureCount;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "not_for_me_at")
	private Instant notForMeAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserReviewUnitState() {
	}

	public UserReviewUnitState(User user, ReviewPoint reviewUnit, Instant admittedAt) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.reviewUnit = reviewUnit;
		this.status = UserReviewUnitStatus.PENDING_FIRST_REVIEW;
		this.admittedAt = admittedAt;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public ReviewPoint getReviewUnit() {
		return reviewUnit;
	}

	public UserReviewUnitStatus getStatus() {
		return status;
	}

	public Instant getAdmittedAt() {
		return admittedAt;
	}

	public Instant getFirstReviewedAt() {
		return firstReviewedAt;
	}

	public Instant getLastReviewedAt() {
		return lastReviewedAt;
	}

	public Instant getNextReviewAt() {
		return nextReviewAt;
	}

	public ReviewAttemptResult getLastResult() {
		return lastResult;
	}

	public int getConsecutiveSuccessCount() {
		return consecutiveSuccessCount;
	}

	public int getConsecutiveFailureCount() {
		return consecutiveFailureCount;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public Instant getNotForMeAt() {
		return notForMeAt;
	}

	public void recordAttempt(ReviewAttemptResult result, Instant attemptedAt, Instant nextReviewAt) {
		if (firstReviewedAt == null) {
			firstReviewedAt = attemptedAt;
		}
		status = UserReviewUnitStatus.ACTIVE;
		lastReviewedAt = attemptedAt;
		this.nextReviewAt = nextReviewAt;
		lastResult = result;
		if (result == ReviewAttemptResult.GOOD || result == ReviewAttemptResult.SELF_MASTERED) {
			consecutiveSuccessCount++;
			consecutiveFailureCount = 0;
		} else {
			consecutiveFailureCount++;
			consecutiveSuccessCount = 0;
		}
	}

	public void postpone(Instant nextReviewAt) {
		this.nextReviewAt = nextReviewAt;
	}

	public void archive(Instant archivedAt) {
		status = UserReviewUnitStatus.ARCHIVED;
		this.archivedAt = archivedAt;
	}

	public void markNotForMe(Instant notForMeAt) {
		status = UserReviewUnitStatus.NOT_FOR_ME;
		this.notForMeAt = notForMeAt;
	}
}

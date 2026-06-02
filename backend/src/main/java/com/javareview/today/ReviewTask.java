package com.javareview.today;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.persistence.Table;

import com.javareview.auth.User;
import com.javareview.reviewpoint.ReviewPoint;

@Entity
@Table(name = "review_tasks")
public class ReviewTask {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_point_id")
	private ReviewPoint reviewPoint;

	@Column(name = "manual_prompt")
	private String manualPrompt;

	@Column(name = "task_date", nullable = false)
	private LocalDate taskDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewTaskType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

	@Column(name = "priority_score", nullable = false, precision = 6, scale = 2)
	private BigDecimal priorityScore;

	@Column(name = "estimated_minutes", nullable = false)
	private int estimatedMinutes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected ReviewTask() {
	}

	public ReviewTask(
			User user,
			ReviewPoint reviewPoint,
			LocalDate taskDate,
			ReviewTaskType type,
			BigDecimal priorityScore,
			int estimatedMinutes) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.reviewPoint = reviewPoint;
		this.taskDate = taskDate;
		this.type = type;
		this.priorityScore = priorityScore;
		this.estimatedMinutes = estimatedMinutes;
	}

	public ReviewTask(
			User user,
			String manualPrompt,
			LocalDate taskDate,
			ReviewTaskType type,
			BigDecimal priorityScore,
			int estimatedMinutes) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.manualPrompt = manualPrompt;
		this.taskDate = taskDate;
		this.type = type;
		this.priorityScore = priorityScore;
		this.estimatedMinutes = estimatedMinutes;
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public ReviewPoint getReviewPoint() {
		return reviewPoint;
	}

	public String getManualPrompt() {
		return manualPrompt;
	}

	public LocalDate getTaskDate() {
		return taskDate;
	}

	public ReviewTaskType getType() {
		return type;
	}

	public ReviewTaskStatus getStatus() {
		return status;
	}

	public BigDecimal getPriorityScore() {
		return priorityScore;
	}

	public int getEstimatedMinutes() {
		return estimatedMinutes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void skip(Instant skippedAt) {
		this.status = ReviewTaskStatus.SKIPPED;
		this.completedAt = skippedAt;
	}
}

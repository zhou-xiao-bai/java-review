package com.javareview.reviewunit;

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
@Table(name = "today_review_actions")
public class TodayReviewAction {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_unit_id", nullable = false)
	private ReviewPoint reviewUnit;

	@Column(name = "action_date", nullable = false)
	private LocalDate actionDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 32)
	private TodayReviewActionType actionType;

	@Column(name = "postpone_until")
	private LocalDate postponeUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TodayReviewAction() {
	}

	public TodayReviewAction(
			User user,
			ReviewPoint reviewUnit,
			LocalDate actionDate,
			TodayReviewActionType actionType,
			LocalDate postponeUntil) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.reviewUnit = reviewUnit;
		this.actionDate = actionDate;
		this.actionType = actionType;
		this.postponeUntil = postponeUntil;
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

	public ReviewPoint getReviewUnit() {
		return reviewUnit;
	}

	public LocalDate getActionDate() {
		return actionDate;
	}

	public TodayReviewActionType getActionType() {
		return actionType;
	}

	public LocalDate getPostponeUntil() {
		return postponeUntil;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

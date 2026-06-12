package com.javareview.reviewsession;

import java.math.BigDecimal;
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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.javareview.auth.User;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewunit.QuestionVariant;
import com.javareview.reviewunit.UserReviewUnitState;

@Entity
@Table(name = "review_sessions")
public class ReviewSession {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_unit_state_id", nullable = false)
	private UserReviewUnitState reviewUnitState;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_variant_id")
	private QuestionVariant questionVariant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewSessionStatus status = ReviewSessionStatus.ACTIVE;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "final_score", precision = 3, scale = 2)
	private BigDecimal finalScore;

	@Column
	private String summary;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private ReviewEvaluation evaluation;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ReviewSession() {
	}

	public ReviewSession(User user, UserReviewUnitState reviewUnitState, Instant startedAt) {
		this(user, reviewUnitState, null, startedAt);
	}

	public ReviewSession(User user, UserReviewUnitState reviewUnitState, QuestionVariant questionVariant, Instant startedAt) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.reviewUnitState = reviewUnitState;
		this.questionVariant = questionVariant;
		this.startedAt = startedAt;
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

	public UserReviewUnitState getReviewUnitState() {
		return reviewUnitState;
	}

	public ReviewPoint getReviewUnit() {
		return reviewUnitState.getReviewUnit();
	}

	public QuestionVariant getQuestionVariant() {
		return questionVariant;
	}

	public ReviewSessionStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BigDecimal getFinalScore() {
		return finalScore;
	}

	public String getSummary() {
		return summary;
	}

	public ReviewEvaluation getEvaluation() {
		return evaluation;
	}

	public void evaluate(ReviewEvaluation evaluation, Instant endedAt) {
		this.status = ReviewSessionStatus.EVALUATED;
		this.evaluation = evaluation;
		this.finalScore = evaluation.score().overall();
		this.summary = evaluation.overallComment();
		this.endedAt = endedAt;
	}

	public void abandon(Instant endedAt) {
		this.status = ReviewSessionStatus.ABANDONED;
		this.endedAt = endedAt;
	}
}

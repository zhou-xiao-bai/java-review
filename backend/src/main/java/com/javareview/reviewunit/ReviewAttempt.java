package com.javareview.reviewunit;

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

import com.javareview.auth.User;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewsession.ReviewSession;

@Entity
@Table(name = "review_attempts")
public class ReviewAttempt {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_unit_id", nullable = false)
	private ReviewPoint reviewUnit;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "review_session_id")
	private ReviewSession reviewSession;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_variant_id")
	private QuestionVariant questionVariant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewAttemptSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewAttemptResult result;

	@Column(precision = 3, scale = 2)
	private BigDecimal score;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	@Column
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ReviewAttempt() {
	}

	public ReviewAttempt(
			User user,
			ReviewPoint reviewUnit,
			ReviewSession reviewSession,
			ReviewAttemptSource source,
			ReviewAttemptResult result,
			BigDecimal score,
			Instant attemptedAt,
			String note) {
		this(user, reviewUnit, reviewSession, null, source, result, score, attemptedAt, note);
	}

	public ReviewAttempt(
			User user,
			ReviewPoint reviewUnit,
			ReviewSession reviewSession,
			QuestionVariant questionVariant,
			ReviewAttemptSource source,
			ReviewAttemptResult result,
			BigDecimal score,
			Instant attemptedAt,
			String note) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.reviewUnit = reviewUnit;
		this.reviewSession = reviewSession;
		this.questionVariant = questionVariant;
		this.source = source;
		this.result = result;
		this.score = score;
		this.attemptedAt = attemptedAt;
		this.note = note;
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public QuestionVariant getQuestionVariant() {
		return questionVariant;
	}
}

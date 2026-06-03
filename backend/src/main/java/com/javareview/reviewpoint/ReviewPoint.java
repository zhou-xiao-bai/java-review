package com.javareview.reviewpoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.javareview.topic.Topic;

@Entity
@Table(name = "review_points")
public class ReviewPoint {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_id", nullable = false)
	private Topic topic;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(nullable = false)
	private int importance;

	@Column(nullable = false)
	private int difficulty;

	@Column(name = "interview_frequency", nullable = false)
	private int interviewFrequency;

	@Column(nullable = false, precision = 3, scale = 2)
	private BigDecimal mastery = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewPointStatus status = ReviewPointStatus.UNCOVERED;

	@Column(name = "last_reviewed_at")
	private Instant lastReviewedAt;

	@Column(name = "next_review_at")
	private Instant nextReviewAt;

	@Column(name = "review_count", nullable = false)
	private int reviewCount;

	@Column(name = "wrong_count", nullable = false)
	private int wrongCount;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "weak_points", nullable = false, columnDefinition = "jsonb")
	private List<String> weakPoints = new ArrayList<>();

	@Column(name = "next_probe")
	private String nextProbe;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recent_question_types", nullable = false, columnDefinition = "jsonb")
	private List<String> recentQuestionTypes = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ReviewPoint() {
	}

	public ReviewPoint(
			Topic topic,
			String title,
			int importance,
			int difficulty,
			int interviewFrequency,
			String nextProbe) {
		this.id = UUID.randomUUID();
		this.topic = topic;
		this.title = title;
		this.importance = importance;
		this.difficulty = difficulty;
		this.interviewFrequency = interviewFrequency;
		this.nextProbe = nextProbe;
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

	public Topic getTopic() {
		return topic;
	}

	public String getTitle() {
		return title;
	}

	public int getImportance() {
		return importance;
	}

	public int getDifficulty() {
		return difficulty;
	}

	public int getInterviewFrequency() {
		return interviewFrequency;
	}

	public BigDecimal getMastery() {
		return mastery;
	}

	public ReviewPointStatus getStatus() {
		return status;
	}

	public Instant getLastReviewedAt() {
		return lastReviewedAt;
	}

	public Instant getNextReviewAt() {
		return nextReviewAt;
	}

	public int getReviewCount() {
		return reviewCount;
	}

	public int getWrongCount() {
		return wrongCount;
	}

	public List<String> getWeakPoints() {
		return weakPoints;
	}

	public String getNextProbe() {
		return nextProbe;
	}

	public void updateReviewProgress(
			BigDecimal mastery,
			ReviewPointStatus status,
			Instant lastReviewedAt,
			Instant nextReviewAt,
			int reviewCount,
			int wrongCount,
			List<String> weakPoints,
			String nextProbe) {
		this.mastery = mastery;
		this.status = status;
		this.lastReviewedAt = lastReviewedAt;
		this.nextReviewAt = nextReviewAt;
		this.reviewCount = reviewCount;
		this.wrongCount = wrongCount;
		this.weakPoints = new ArrayList<>(weakPoints);
		this.nextProbe = nextProbe;
	}
}

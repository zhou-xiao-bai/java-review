package com.javareview.reviewpoint;

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

import com.javareview.reviewsession.ReviewSession;
import com.javareview.reviewsession.ReviewTurn;

@Entity
@Table(name = "review_weakness_events")
public class ReviewWeaknessEvent {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_point_id", nullable = false)
	private ReviewPoint reviewPoint;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private ReviewSession session;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "turn_id")
	private ReviewTurn turn;

	@Column(nullable = false, length = 80)
	private String category;

	@Column(nullable = false, columnDefinition = "text")
	private String label;

	@Column(columnDefinition = "text")
	private String evidence;

	@Column(nullable = false)
	private int severity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WeaknessEventStatus status = WeaknessEventStatus.OPEN;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected ReviewWeaknessEvent() {
	}

	public ReviewWeaknessEvent(
			ReviewPoint reviewPoint,
			ReviewSession session,
			ReviewTurn turn,
			String category,
			String label,
			String evidence,
			int severity) {
		this.id = UUID.randomUUID();
		this.reviewPoint = reviewPoint;
		this.session = session;
		this.turn = turn;
		this.category = category;
		this.label = label;
		this.evidence = evidence;
		this.severity = Math.max(1, Math.min(5, severity));
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ReviewPoint getReviewPoint() {
		return reviewPoint;
	}

	public ReviewSession getSession() {
		return session;
	}

	public ReviewTurn getTurn() {
		return turn;
	}

	public String getCategory() {
		return category;
	}

	public String getLabel() {
		return label;
	}

	public String getEvidence() {
		return evidence;
	}

	public int getSeverity() {
		return severity;
	}

	public WeaknessEventStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

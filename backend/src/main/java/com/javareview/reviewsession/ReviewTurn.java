package com.javareview.reviewsession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

@Entity
@Table(name = "review_turns")
public class ReviewTurn {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private ReviewSession session;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ReviewTurnRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "turn_type", nullable = false, length = 32)
	private ReviewTurnType turnType;

	@Column(nullable = false)
	private String content;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata = new HashMap<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ReviewTurn() {
	}

	public ReviewTurn(ReviewSession session, ReviewTurnRole role, ReviewTurnType turnType, String content) {
		this.id = UUID.randomUUID();
		this.session = session;
		this.role = role;
		this.turnType = turnType;
		this.content = content;
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ReviewTurnRole getRole() {
		return role;
	}

	public ReviewTurnType getTurnType() {
		return turnType;
	}

	public String getContent() {
		return content;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

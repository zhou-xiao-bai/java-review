package com.javareview.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_turns")
public class ProjectTurn {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private ProjectSession session;

	@Column(nullable = false, length = 32)
	private String role;

	@Column(nullable = false)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProjectTurn() {
	}

	public ProjectTurn(ProjectSession session, String role, String content) {
		this.id = UUID.randomUUID();
		this.session = session;
		this.role = role;
		this.content = content;
	}

	@PrePersist
	void prePersist() { this.createdAt = Instant.now(); }
	public UUID getId() { return id; }
	public String getRole() { return role; }
	public String getContent() { return content; }
	public Instant getCreatedAt() { return createdAt; }
}

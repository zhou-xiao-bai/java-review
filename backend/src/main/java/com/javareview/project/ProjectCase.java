package com.javareview.project;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.javareview.auth.User;

@Entity
@Table(name = "project_cases")
public class ProjectCase {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 160)
	private String name;

	@Column
	private String background;

	@Column
	private String responsibility;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "tech_stack", nullable = false, columnDefinition = "jsonb")
	private List<String> techStack = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<String> highlights = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "weak_points", nullable = false, columnDefinition = "jsonb")
	private List<String> weakPoints = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProjectCase() {
	}

	public ProjectCase(User user, String name, String background, String responsibility, List<String> techStack, List<String> highlights) {
		this.id = UUID.randomUUID();
		this.user = user;
		update(name, background, responsibility, techStack, highlights);
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

	public UUID getId() { return id; }
	public User getUser() { return user; }
	public String getName() { return name; }
	public String getBackground() { return background; }
	public String getResponsibility() { return responsibility; }
	public List<String> getTechStack() { return techStack; }
	public List<String> getHighlights() { return highlights; }
	public List<String> getWeakPoints() { return weakPoints; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }

	public void update(String name, String background, String responsibility, List<String> techStack, List<String> highlights) {
		this.name = name;
		this.background = background;
		this.responsibility = responsibility;
		this.techStack = new ArrayList<>(techStack == null ? List.of() : techStack);
		this.highlights = new ArrayList<>(highlights == null ? List.of() : highlights);
	}

	public void setWeakPoints(List<String> weakPoints) {
		this.weakPoints = new ArrayList<>(weakPoints == null ? List.of() : weakPoints);
	}
}

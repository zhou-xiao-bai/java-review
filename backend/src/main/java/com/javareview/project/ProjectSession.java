package com.javareview.project;

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
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.javareview.auth.User;

@Entity
@Table(name = "project_sessions")
public class ProjectSession {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_case_id", nullable = false)
	private ProjectCase projectCase;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ProjectSessionStatus status = ProjectSessionStatus.ACTIVE;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "final_score", precision = 3, scale = 2)
	private BigDecimal finalScore;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private ProjectEvaluation evaluation;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "suggested_topics", nullable = false, columnDefinition = "jsonb")
	private List<String> suggestedTopics = new ArrayList<>();

	protected ProjectSession() {
	}

	public ProjectSession(User user, ProjectCase projectCase, Instant startedAt) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.projectCase = projectCase;
		this.startedAt = startedAt;
	}

	public UUID getId() { return id; }
	public User getUser() { return user; }
	public ProjectCase getProjectCase() { return projectCase; }
	public ProjectSessionStatus getStatus() { return status; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getEndedAt() { return endedAt; }
	public BigDecimal getFinalScore() { return finalScore; }
	public ProjectEvaluation getEvaluation() { return evaluation; }
	public List<String> getSuggestedTopics() { return suggestedTopics; }

	public void evaluate(ProjectEvaluation evaluation, Instant endedAt) {
		this.status = ProjectSessionStatus.EVALUATED;
		this.evaluation = evaluation;
		this.finalScore = evaluation.score().overall();
		this.suggestedTopics = new ArrayList<>(evaluation.suggestedTopics());
		this.endedAt = endedAt;
		this.projectCase.setWeakPoints(evaluation.weakPoints());
	}
}

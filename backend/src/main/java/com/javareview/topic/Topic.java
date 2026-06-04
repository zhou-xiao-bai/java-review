package com.javareview.topic;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "topics")
public class Topic {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "domain_id", nullable = false)
	private Domain domain;

	@Column(nullable = false, unique = true, length = 96)
	private String code;

	@Column(nullable = false, length = 120)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TopicSource source;

	@Column(nullable = false)
	private boolean selected;

	@Enumerated(EnumType.STRING)
	@Column(name = "relevance_tier", nullable = false, length = 32)
	private RelevanceTier relevanceTier = RelevanceTier.CORE;

	@Column(name = "plan_enabled", nullable = false)
	private boolean planEnabled = true;

	@Column(name = "interview_value", nullable = false)
	private int interviewValue = 3;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Topic() {
	}

	public Topic(Domain domain, String code, String title, TopicSource source, boolean selected) {
		this.id = UUID.randomUUID();
		this.domain = domain;
		this.code = code;
		this.title = title;
		this.source = source;
		this.selected = selected;
		if (source == TopicSource.MANUAL) {
			this.relevanceTier = RelevanceTier.PROJECT;
			this.interviewValue = 4;
		}
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

	public Domain getDomain() {
		return domain;
	}

	public String getCode() {
		return code;
	}

	public String getTitle() {
		return title;
	}

	public TopicSource getSource() {
		return source;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public RelevanceTier getRelevanceTier() {
		return relevanceTier;
	}

	public boolean isPlanEnabled() {
		return planEnabled;
	}

	public int getInterviewValue() {
		return interviewValue;
	}

	public boolean isAutoPlannable() {
		return selected && planEnabled && relevanceTier.autoPlannable();
	}

	public void updatePlanning(RelevanceTier relevanceTier, boolean planEnabled, int interviewValue) {
		this.relevanceTier = relevanceTier;
		this.planEnabled = planEnabled;
		this.interviewValue = interviewValue;
	}
}

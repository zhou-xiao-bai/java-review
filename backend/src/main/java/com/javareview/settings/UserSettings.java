package com.javareview.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.javareview.auth.User;

@Entity
@Table(name = "user_settings")
public class UserSettings {

	@Id
	@Column(name = "user_id")
	private java.util.UUID userId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "llm_provider", nullable = false, length = 40)
	private String llmProvider = "openai-compatible";

	@Column(name = "llm_base_url", length = 500)
	private String llmBaseUrl = "https://api.openai.com/v1";

	@Column(name = "llm_api_key")
	private String llmApiKey;

	@Column(name = "llm_model", length = 120)
	private String llmModel = "gpt-4o-mini";

	@Column(name = "request_timeout_seconds", nullable = false)
	private int requestTimeoutSeconds = 30;

	@Column(name = "daily_review_minutes", nullable = false)
	private int dailyReviewMinutes = 60;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserSettings() {
	}

	public UserSettings(User user) {
		this.user = user;
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

	public User getUser() {
		return user;
	}

	public String getLlmProvider() {
		return llmProvider;
	}

	public String getLlmBaseUrl() {
		return llmBaseUrl;
	}

	public String getLlmApiKey() {
		return llmApiKey;
	}

	public String getLlmModel() {
		return llmModel;
	}

	public int getRequestTimeoutSeconds() {
		return requestTimeoutSeconds;
	}

	public int getDailyReviewMinutes() {
		return dailyReviewMinutes;
	}

	public void update(
			String llmProvider,
			String llmBaseUrl,
			String llmApiKey,
			boolean replaceApiKey,
			String llmModel,
			int requestTimeoutSeconds,
			int dailyReviewMinutes) {
		this.llmProvider = llmProvider;
		this.llmBaseUrl = llmBaseUrl;
		if (replaceApiKey) {
			this.llmApiKey = llmApiKey;
		}
		this.llmModel = llmModel;
		this.requestTimeoutSeconds = requestTimeoutSeconds;
		this.dailyReviewMinutes = dailyReviewMinutes;
	}
}

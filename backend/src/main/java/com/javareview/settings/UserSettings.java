package com.javareview.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	@Column(name = "active_llm_config_id", length = 80)
	private String activeLlmConfigId = "default";

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "llm_configs", nullable = false, columnDefinition = "jsonb")
	private List<LlmConfig> llmConfigs = new ArrayList<>();

	@Column(name = "request_timeout_seconds", nullable = false)
	private int requestTimeoutSeconds = 30;

	@Column(name = "daily_review_minutes", nullable = false)
	private int dailyReviewMinutes = 60;

	@Column(name = "reviewed_point_scheduling_policy", nullable = false, length = 32)
	private String reviewedPointSchedulingPolicy = ReviewedPointSchedulingPolicy.FOLLOW_SCOPE.apiValue();

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
		return activeConfig().provider();
	}

	public String getLlmBaseUrl() {
		return activeConfig().baseUrl();
	}

	public String getLlmApiKey() {
		return activeConfig().apiKey();
	}

	public String getLlmModel() {
		return activeConfig().model();
	}

	public String getActiveLlmConfigId() {
		return activeConfig().id();
	}

	public List<LlmConfig> getLlmConfigs() {
		return normalizedConfigs();
	}

	public int getRequestTimeoutSeconds() {
		return requestTimeoutSeconds;
	}

	public int getDailyReviewMinutes() {
		return dailyReviewMinutes;
	}

	public ReviewedPointSchedulingPolicy getReviewedPointSchedulingPolicy() {
		return ReviewedPointSchedulingPolicy.fromApiValue(reviewedPointSchedulingPolicy);
	}

	public void update(
			String activeLlmConfigId,
			List<LlmConfig> llmConfigs,
			int requestTimeoutSeconds,
			int dailyReviewMinutes) {
		update(
				activeLlmConfigId,
				llmConfigs,
				requestTimeoutSeconds,
				dailyReviewMinutes,
				ReviewedPointSchedulingPolicy.FOLLOW_SCOPE);
	}

	public void update(
			String activeLlmConfigId,
			List<LlmConfig> llmConfigs,
			int requestTimeoutSeconds,
			int dailyReviewMinutes,
			ReviewedPointSchedulingPolicy reviewedPointSchedulingPolicy) {
		this.llmConfigs = new ArrayList<>(llmConfigs);
		this.activeLlmConfigId = activeLlmConfigId;
		LlmConfig active = activeConfig();
		this.llmProvider = active.provider();
		this.llmBaseUrl = active.baseUrl();
		this.llmApiKey = active.apiKey();
		this.llmModel = active.model();
		this.requestTimeoutSeconds = requestTimeoutSeconds;
		this.dailyReviewMinutes = dailyReviewMinutes;
		this.reviewedPointSchedulingPolicy = reviewedPointSchedulingPolicy.apiValue();
	}

	private LlmConfig activeConfig() {
		return normalizedConfigs().stream()
				.filter(config -> config.id().equals(activeLlmConfigId))
				.findFirst()
				.orElseGet(() -> normalizedConfigs().getFirst());
	}

	private List<LlmConfig> normalizedConfigs() {
		if (llmConfigs == null || llmConfigs.isEmpty()) {
			return List.of(new LlmConfig("default", "默认中转站", llmProvider, llmBaseUrl, llmApiKey, llmModel));
		}
		return llmConfigs;
	}
}

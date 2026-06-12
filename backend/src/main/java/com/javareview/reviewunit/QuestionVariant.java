package com.javareview.reviewunit;

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

import com.javareview.reviewpoint.ReviewPoint;

@Entity
@Table(name = "question_variants")
public class QuestionVariant {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_unit_id", nullable = false)
	private ReviewPoint reviewUnit;

	@Column(nullable = false, length = 180)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String prompt;

	@Column(length = 240)
	private String focus;

	@Column(nullable = false)
	private int difficulty;

	@Enumerated(EnumType.STRING)
	@Column(name = "variant_type", nullable = false, length = 32)
	private QuestionVariantType variantType = QuestionVariantType.CORE_DIAGNOSTIC;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected QuestionVariant() {
	}

	public QuestionVariant(
			ReviewPoint reviewUnit,
			String title,
			String prompt,
			String focus,
			int difficulty,
			QuestionVariantType variantType) {
		this.id = UUID.randomUUID();
		this.reviewUnit = reviewUnit;
		this.title = title;
		this.prompt = prompt;
		this.focus = focus;
		this.difficulty = difficulty;
		this.variantType = variantType;
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

	public ReviewPoint getReviewUnit() {
		return reviewUnit;
	}

	public String getTitle() {
		return title;
	}

	public String getPrompt() {
		return prompt;
	}

	public String getFocus() {
		return focus;
	}

	public int getDifficulty() {
		return difficulty;
	}

	public QuestionVariantType getVariantType() {
		return variantType;
	}

	public boolean isEnabled() {
		return enabled;
	}
}

package com.javareview.reviewunit;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javareview.auth.User;
import com.javareview.reviewpoint.ReviewPoint;

@Service
public class QuestionVariantSelectionService {

	private static final int RECENT_VARIANT_LIMIT = 5;

	private final QuestionVariantRepository questionVariantRepository;
	private final ReviewAttemptRepository reviewAttemptRepository;

	public QuestionVariantSelectionService(
			QuestionVariantRepository questionVariantRepository,
			ReviewAttemptRepository reviewAttemptRepository) {
		this.questionVariantRepository = questionVariantRepository;
		this.reviewAttemptRepository = reviewAttemptRepository;
	}

	@Transactional(readOnly = true)
	public Optional<QuestionVariant> selectFor(User user, ReviewPoint reviewUnit) {
		List<QuestionVariant> variants = questionVariantRepository.findByReviewUnitIdAndEnabledTrue(reviewUnit.getId());
		if (variants.isEmpty()) {
			return Optional.empty();
		}
		Set<UUID> recentVariantIds = new HashSet<>(reviewAttemptRepository.findRecentQuestionVariantIds(
				user.getId(),
				reviewUnit.getId(),
				PageRequest.of(0, RECENT_VARIANT_LIMIT)));
		List<QuestionVariant> candidates = variants.stream()
				.filter(variant -> !recentVariantIds.contains(variant.getId()))
				.toList();
		if (candidates.isEmpty()) {
			candidates = variants;
		}
		return candidates.stream()
				.min(Comparator
						.comparingInt(this::variantTypeRank)
						.thenComparingInt(variant -> Math.abs(variant.getDifficulty() - reviewUnit.getDifficulty()))
						.thenComparing(QuestionVariant::getTitle)
						.thenComparing(QuestionVariant::getId));
	}

	private int variantTypeRank(QuestionVariant variant) {
		return switch (variant.getVariantType()) {
			case CORE_DIAGNOSTIC -> 1;
			case SCENARIO -> 2;
			case TROUBLESHOOTING -> 3;
			case COMPARISON -> 4;
			case EXPANSION -> 5;
		};
	}
}

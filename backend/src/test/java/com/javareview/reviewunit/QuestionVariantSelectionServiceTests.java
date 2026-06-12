package com.javareview.reviewunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.topic.Domain;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class QuestionVariantSelectionServiceTests {

	@Mock
	private QuestionVariantRepository questionVariantRepository;

	@Mock
	private ReviewAttemptRepository reviewAttemptRepository;

	private QuestionVariantSelectionService selectionService;
	private User user;
	private ReviewPoint unit;

	@BeforeEach
	void setUp() {
		selectionService = new QuestionVariantSelectionService(questionVariantRepository, reviewAttemptRepository);
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		Topic topic = new Topic(new Domain(java.util.UUID.randomUUID(), "mysql", "MySQL", 80),
				"mysql-indexes", "索引", TopicSource.BUILTIN, true);
		unit = new ReviewPoint(topic, "B+Tree 查询路径", 5, 4, 5, "next probe");
	}

	@Test
	void selectsEnabledVariantThatWasNotRecentlyAsked() {
		QuestionVariant recent = variant("核心诊断", 4, QuestionVariantType.CORE_DIAGNOSTIC);
		QuestionVariant candidate = variant("范围查询场景", 4, QuestionVariantType.SCENARIO);
		when(questionVariantRepository.findByReviewUnitIdAndEnabledTrue(unit.getId()))
				.thenReturn(List.of(recent, candidate));
		when(reviewAttemptRepository.findRecentQuestionVariantIds(eq(user.getId()), eq(unit.getId()), any()))
				.thenReturn(List.of(recent.getId()));

		var selected = selectionService.selectFor(user, unit);

		assertThat(selected).contains(candidate);
	}

	@Test
	void fallsBackToRecentPoolWhenEveryVariantWasRecentlyAsked() {
		QuestionVariant recent = variant("核心诊断", 4, QuestionVariantType.CORE_DIAGNOSTIC);
		when(questionVariantRepository.findByReviewUnitIdAndEnabledTrue(unit.getId()))
				.thenReturn(List.of(recent));
		when(reviewAttemptRepository.findRecentQuestionVariantIds(eq(user.getId()), eq(unit.getId()), any()))
				.thenReturn(List.of(recent.getId()));

		var selected = selectionService.selectFor(user, unit);

		assertThat(selected).contains(recent);
	}

	@Test
	void returnsEmptyWhenUnitHasNoVariant() {
		when(questionVariantRepository.findByReviewUnitIdAndEnabledTrue(unit.getId())).thenReturn(List.of());

		var selected = selectionService.selectFor(user, unit);

		assertThat(selected).isEmpty();
	}

	private QuestionVariant variant(String title, int difficulty, QuestionVariantType type) {
		return new QuestionVariant(unit, title, "prompt " + Instant.now(), title, difficulty, type);
	}
}

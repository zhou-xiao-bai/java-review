package com.javareview.reviewunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.common.ResourceNotFoundException;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.topic.Domain;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicRepository;
import com.javareview.topic.TopicSource;
import com.javareview.reviewunit.ReviewUnitDtos.AdmitReviewUnitsRequest;
import com.javareview.reviewunit.ReviewUnitDtos.ReviewUnitsResponse;

@ExtendWith(MockitoExtension.class)
class ReviewUnitServiceTests {

	private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

	@Mock
	private TopicRepository topicRepository;

	@Mock
	private ReviewPointRepository reviewPointRepository;

	@Mock
	private UserReviewUnitStateRepository stateRepository;

	private ReviewUnitService reviewUnitService;
	private User user;
	private Domain domain;
	private Topic topic;

	@BeforeEach
	void setUp() {
		reviewUnitService = new ReviewUnitService(
				topicRepository,
				reviewPointRepository,
				stateRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		domain = new Domain(UUID.randomUUID(), "mysql", "MySQL", 80);
		topic = new Topic(domain, "mysql-index", "MySQL 索引", TopicSource.BUILTIN, true);
	}

	@Test
	void listTopicReviewUnitsReturnsAdmissionState() {
		ReviewPoint admittedUnit = reviewUnit("B+Tree 查询路径", 5, 4, 5);
		ReviewPoint unadmittedUnit = reviewUnit("覆盖索引与回表", 4, 3, 4);
		UserReviewUnitState state = new UserReviewUnitState(user, admittedUnit, NOW.minusSeconds(3600));
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(List.of(admittedUnit, unadmittedUnit));
		when(stateRepository.findByUserIdAndReviewUnitIdIn(eq(user.getId()), anyCollection()))
				.thenReturn(List.of(state));

		ReviewUnitsResponse response = reviewUnitService.listTopicReviewUnits(user, topic.getId());

		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.admittedCount()).isEqualTo(1);
		assertThat(response.pendingFirstReviewCount()).isEqualTo(1);
		assertThat(response.units())
				.extracting(unit -> unit.stateStatus() == null ? "UNADMITTED" : unit.stateStatus())
				.containsExactly("PENDING_FIRST_REVIEW", "UNADMITTED");
	}

	@Test
	void admitTopicReviewUnitsCreatesOnlyMissingStates() {
		ReviewPoint existingUnit = reviewUnit("B+Tree 查询路径", 5, 4, 5);
		ReviewPoint newUnit = reviewUnit("覆盖索引与回表", 4, 3, 4);
		UserReviewUnitState existingState = new UserReviewUnitState(user, existingUnit, NOW.minusSeconds(3600));
		List<UserReviewUnitState> states = new ArrayList<>(List.of(existingState));
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(List.of(existingUnit, newUnit));
		when(stateRepository.findByUserIdAndReviewUnitIdIn(eq(user.getId()), anyCollection()))
				.thenAnswer(invocation -> List.copyOf(states));
		when(stateRepository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Iterable<UserReviewUnitState> savedStates = invocation.getArgument(0);
			savedStates.forEach(states::add);
			return savedStates;
		});

		ReviewUnitsResponse response = reviewUnitService.admitTopicReviewUnits(
				user,
				topic.getId(),
				new AdmitReviewUnitsRequest(null));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserReviewUnitState>> captor = ArgumentCaptor.forClass(List.class);
		verify(stateRepository).saveAll(captor.capture());
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst().getReviewUnit()).isEqualTo(newUnit);
		assertThat(captor.getValue().getFirst().getAdmittedAt()).isEqualTo(NOW);
		assertThat(response.admittedCount()).isEqualTo(2);
	}

	@Test
	void admitTopicReviewUnitsWithoutUnitsDoesNotWriteState() {
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(List.of());

		ReviewUnitsResponse response = reviewUnitService.admitTopicReviewUnits(
				user,
				topic.getId(),
				new AdmitReviewUnitsRequest(null));

		assertThat(response.totalCount()).isZero();
		verify(stateRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void admitSpecificReviewUnitRejectsUnitOutsideTopic() {
		ReviewPoint unit = reviewUnit("B+Tree 查询路径", 5, 4, 5);
		when(topicRepository.findById(topic.getId())).thenReturn(Optional.of(topic));
		when(reviewPointRepository.findByTopicId(topic.getId())).thenReturn(List.of(unit));

		assertThatThrownBy(() -> reviewUnitService.admitTopicReviewUnits(
				user,
				topic.getId(),
				new AdmitReviewUnitsRequest(List.of(UUID.randomUUID()))))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Review unit not found in topic.");
	}

	private ReviewPoint reviewUnit(String title, int importance, int difficulty, int interviewFrequency) {
		return new ReviewPoint(topic, title, importance, difficulty, interviewFrequency, "next probe");
	}
}

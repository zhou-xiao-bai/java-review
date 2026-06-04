package com.javareview.today;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.javareview.auth.User;
import com.javareview.auth.UserRole;
import com.javareview.reviewpoint.ReviewPoint;
import com.javareview.reviewpoint.ReviewPointRepository;
import com.javareview.reviewpoint.ReviewPointStatus;
import com.javareview.settings.SettingsService;
import com.javareview.settings.UserSettings;
import com.javareview.today.TodayDtos.TodayPlanResponse;
import com.javareview.topic.Domain;
import com.javareview.topic.RelevanceTier;
import com.javareview.topic.Topic;
import com.javareview.topic.TopicSource;

@ExtendWith(MockitoExtension.class)
class TodayPlanServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);

	@Mock
	private ReviewTaskRepository reviewTaskRepository;

	@Mock
	private ReviewPointRepository reviewPointRepository;

	@Mock
	private SettingsService settingsService;

	private TodayPlanService todayPlanService;
	private User user;
	private Topic topic;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
		user = new User("admin", "admin@example.com", "hash", "Admin", UserRole.ADMIN);
		UserSettings settings = new UserSettings(user);
		lenient().when(settingsService.findOrDefault(user)).thenReturn(settings);
		todayPlanService = new TodayPlanService(
				reviewTaskRepository,
				reviewPointRepository,
				new ReviewPriorityService(clock),
				settingsService,
				clock);
		topic = new Topic(new Domain(java.util.UUID.randomUUID(), "spring", "Spring", 40),
				"spring-transactions", "Spring 事务", TopicSource.BUILTIN, true);
	}

	@Test
	void getTodayDoesNotGeneratePlanWhenNoTasksExist() {
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());

		TodayPlanResponse response = todayPlanService.getToday(user);

		assertThat(response.scheduledMinutes()).isZero();
		assertThat(response.groups()).allSatisfy(group -> assertThat(group.tasks()).isEmpty());
		verify(reviewTaskRepository).findPlan(user.getId(), TODAY);
		verifyNoInteractions(reviewPointRepository);
	}

	@Test
	void unfinishedTasksAreCarriedOverBeforeDueTasks() {
		ReviewPoint carryPoint = point("事务代理生效边界", 5, 5, 5);
		ReviewPoint duePoint = duePoint("传播行为与嵌套调用", 5, 4, 5, TODAY.minusDays(1));
		ReviewTask oldTask = new ReviewTask(
				user,
				carryPoint,
				TODAY.minusDays(1),
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of(oldTask));
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(List.of(duePoint));
		when(reviewPointRepository.findNewExpansionCandidates(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.groups().get(0).tasks()).hasSize(1);
		assertThat(response.groups().get(0).tasks().get(0).type()).isEqualTo("carry_over");
		assertThat(response.groups().get(0).tasks().get(0).pointTitle()).isEqualTo("事务代理生效边界");
		assertThat(response.groups().get(1).tasks()).hasSize(1);
		assertThat(response.scheduledMinutes()).isEqualTo(20);
	}

	@Test
	void carryOverTasksMustBelongToAutoPlannableTopics() {
		ReviewPoint selectedPoint = point("事务代理生效边界", 5, 5, 5);
		Topic unselectedTopic = new Topic(
				new Domain(java.util.UUID.randomUUID(), "redis", "Redis", 50),
				"redis-cache-consistency",
				"缓存一致性",
				TopicSource.BUILTIN,
				false);
		ReviewPoint unselectedPoint = new ReviewPoint(
				unselectedTopic,
				"缓存双写一致性",
				5,
				5,
				5,
				"next probe");
		Topic supplementTopic = new Topic(
				new Domain(java.util.UUID.randomUUID(), "java-foundation", "Java 基础", 10),
				"java-date-time",
				"java.time",
				TopicSource.BUILTIN,
				true);
		supplementTopic.updatePlanning(RelevanceTier.SUPPLEMENT, false, 1);
		ReviewPoint supplementPoint = new ReviewPoint(
				supplementTopic,
				"java.time API 语义",
				3,
				2,
				1,
				"next probe");
		ReviewTask selectedOldTask = new ReviewTask(
				user,
				selectedPoint,
				TODAY.minusDays(1),
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		ReviewTask unselectedOldTask = new ReviewTask(
				user,
				unselectedPoint,
				TODAY.minusDays(1),
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		ReviewTask supplementOldTask = new ReviewTask(
				user,
				supplementPoint,
				TODAY.minusDays(1),
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of(unselectedOldTask, supplementOldTask, selectedOldTask));
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(List.of());
		when(reviewPointRepository.findNewExpansionCandidates(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.groups().get(0).tasks())
				.extracting(task -> task.pointTitle())
				.containsExactly("事务代理生效边界");
	}

	@Test
	void dueTasksAreSelectedByPriorityOrder() {
		ReviewPoint highPriority = duePoint("生产事务失效排查", 5, 5, 5, TODAY.minusDays(3));
		ReviewPoint lowPriority = duePoint("事务上下文与线程绑定", 2, 2, 2, TODAY.minusDays(1));
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of());
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(List.of(lowPriority, highPriority));
		when(reviewPointRepository.findNewExpansionCandidates(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.groups().get(1).tasks())
				.extracting(task -> task.pointTitle())
				.containsExactly("生产事务失效排查", "事务上下文与线程绑定");
	}

	@Test
	void generatedPlanDoesNotExceedDefaultCapacity() {
		List<ReviewPoint> duePoints = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			duePoints.add(duePoint("到期点 " + index, 5, 5, 5, TODAY.minusDays(index + 1L)));
		}
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of());
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(duePoints);
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.scheduledMinutes()).isEqualTo(60);
		assertThat(response.groups().get(1).tasks()).hasSize(6);
	}

	@Test
	void generatedPlanUsesConfiguredDailyCapacity() {
		UserSettings settings = new UserSettings(user);
		settings.update(
				"default",
				List.of(),
				30,
				120);
		when(settingsService.findOrDefault(user)).thenReturn(settings);
		List<ReviewPoint> duePoints = new ArrayList<>();
		for (int index = 0; index < 14; index++) {
			duePoints.add(duePoint("到期点 " + index, 5, 5, 5, TODAY.minusDays(index + 1L)));
		}
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of());
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(duePoints);
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.capacityMinutes()).isEqualTo(120);
		assertThat(response.scheduledMinutes()).isEqualTo(120);
		assertThat(response.remainingMinutes()).isZero();
		assertThat(response.groups().get(1).tasks()).hasSize(12);
	}

	@Test
	void manualTasksDoNotConsumeTodayPlanCapacity() {
		ReviewTask manualTask = new ReviewTask(
				user,
				"手写 AQS acquire 流程",
				TODAY,
				ReviewTaskType.MANUAL,
				BigDecimal.TEN,
				30);
		List<ReviewPoint> duePoints = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			duePoints.add(duePoint("到期点 " + index, 5, 5, 5, TODAY.minusDays(index + 1L)));
		}
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of(manualTask));
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of());
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(duePoints);
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.generateToday(user);

		assertThat(response.scheduledMinutes()).isEqualTo(60);
		assertThat(response.remainingMinutes()).isZero();
		assertThat(response.groups().get(1).tasks()).hasSize(6);
		assertThat(response.groups().get(3).scheduledMinutes()).isEqualTo(30);
	}

	@Test
	void removedTasksAreHiddenAndDoNotConsumeCapacityButCompletedMinutesRemain() {
		ReviewTask removedCompletedTask = new ReviewTask(
				user,
				point("传播行为与嵌套调用", 5, 4, 5),
				TODAY,
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		removedCompletedTask.complete(Instant.parse("2026-06-02T08:00:00Z"));
		removedCompletedTask.removeFromToday(Instant.parse("2026-06-02T08:10:00Z"));
		ReviewTask visibleTask = new ReviewTask(
				user,
				point("事务代理生效边界", 5, 4, 5),
				TODAY,
				ReviewTaskType.DUE,
				BigDecimal.ONE,
				10);
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of(removedCompletedTask, visibleTask));

		TodayPlanResponse response = todayPlanService.getToday(user);

		assertThat(response.scheduledMinutes()).isEqualTo(10);
		assertThat(response.completedMinutes()).isEqualTo(10);
		assertThat(response.remainingMinutes()).isEqualTo(50);
		assertThat(response.groups().get(1).tasks())
				.extracting(task -> task.pointTitle())
				.containsExactly("事务代理生效边界");
	}

	@Test
	void removeTasksMarksTodayTasksAsRemovedAndReturnsVisiblePlan() {
		ReviewTask task = new ReviewTask(
				user,
				point("传播行为与嵌套调用", 5, 4, 5),
				TODAY,
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		when(reviewTaskRepository.findAllByIdsAndUserIdWithPoint(anyCollection(), eq(user.getId())))
				.thenReturn(List.of(task));
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of(task));

		TodayPlanResponse response = todayPlanService.removeTasks(
				user,
				new TodayDtos.RemoveReviewTasksRequest(List.of(task.getId())));

		assertThat(task.isRemoved()).isTrue();
		assertThat(response.scheduledMinutes()).isZero();
		assertThat(response.groups()).allSatisfy(group -> assertThat(group.tasks()).isEmpty());
	}

	@Test
	void regeneratingPlanClearsPendingGeneratedTasksBeforeFillingPlan() {
		ReviewPoint duePoint = duePoint("传播行为与嵌套调用", 5, 4, 5, TODAY.minusDays(1));
		when(reviewTaskRepository.findPlan(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.findCarryOverCandidates(eq(user.getId()), eq(TODAY), anyCollection()))
				.thenReturn(List.of());
		when(reviewPointRepository.findDueCandidates(eq(user.getId()), eq(TODAY), any()))
				.thenReturn(List.of(duePoint));
		when(reviewPointRepository.findNewExpansionCandidates(user.getId(), TODAY)).thenReturn(List.of());
		when(reviewTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

		TodayPlanResponse response = todayPlanService.regenerateToday(user);

		verify(reviewTaskRepository).deletePendingGeneratedTasks(user.getId(), TODAY);
		assertThat(response.groups().get(1).tasks())
				.extracting(task -> task.pointTitle())
				.containsExactly("传播行为与嵌套调用");
	}

	@Test
	void unskipRestoresSkippedTaskToPending() {
		ReviewTask task = new ReviewTask(
				user,
				point("传播行为与嵌套调用", 5, 4, 5),
				TODAY,
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		task.skip(Instant.parse("2026-06-02T08:00:00Z"));
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId()))
				.thenReturn(Optional.of(task));

		var response = todayPlanService.unskipTask(user, task.getId());

		assertThat(response.status()).isEqualTo("pending");
		assertThat(response.completedAt()).isNull();
		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
		assertThat(task.getCompletedAt()).isNull();
	}

	@Test
	void unskipRejectsTasksThatWereNotSkipped() {
		ReviewTask task = new ReviewTask(
				user,
				point("传播行为与嵌套调用", 5, 4, 5),
				TODAY,
				ReviewTaskType.DUE,
				BigDecimal.TEN,
				10);
		when(reviewTaskRepository.findByIdAndUserIdWithPoint(task.getId(), user.getId()))
				.thenReturn(Optional.of(task));

		assertThatThrownBy(() -> todayPlanService.unskipTask(user, task.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Only skipped tasks can be restored.");

		assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
	}

	private ReviewPoint point(String title, int importance, int difficulty, int frequency) {
		return new ReviewPoint(
				topic,
				title,
				importance,
				difficulty,
				frequency,
				"next probe");
	}

	private ReviewPoint duePoint(String title, int importance, int difficulty, int frequency, LocalDate dueDate) {
		ReviewPoint point = point(title, importance, difficulty, frequency);
		point.updateReviewProgress(
				BigDecimal.ONE,
				ReviewPointStatus.DUE,
				Instant.parse("2026-05-20T00:00:00Z"),
				dueDate.atStartOfDay().toInstant(ZoneOffset.UTC),
				1,
				0,
				List.of(),
				"next probe");
		return point;
	}
}

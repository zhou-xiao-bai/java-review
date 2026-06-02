package com.javareview.today;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, UUID> {

	@Query("""
			select task
			from ReviewTask task
			left join fetch task.reviewPoint point
			left join fetch point.topic topic
			left join fetch topic.domain domain
			where task.user.id = :userId
			and task.taskDate = :taskDate
			order by task.priorityScore desc, task.createdAt asc
			""")
	List<ReviewTask> findPlan(
			@Param("userId") UUID userId,
			@Param("taskDate") LocalDate taskDate);

	@Query("""
			select task
			from ReviewTask task
			left join fetch task.reviewPoint point
			left join fetch point.topic topic
			left join fetch topic.domain domain
			where task.user.id = :userId
			and task.taskDate < :today
			and task.status in :statuses
			order by task.taskDate asc, task.priorityScore desc, task.createdAt asc
			""")
	List<ReviewTask> findCarryOverCandidates(
			@Param("userId") UUID userId,
			@Param("today") LocalDate today,
			@Param("statuses") Collection<ReviewTaskStatus> statuses);
}

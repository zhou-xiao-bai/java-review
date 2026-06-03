package com.javareview.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectCaseRepository extends JpaRepository<ProjectCase, UUID> {

	List<ProjectCase> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<ProjectCase> findByIdAndUserId(UUID id, UUID userId);
}

interface ProjectSessionRepository extends JpaRepository<ProjectSession, UUID> {

	@Query("""
			select session
			from ProjectSession session
			join fetch session.projectCase projectCase
			where session.id = :id
			and session.user.id = :userId
			""")
	Optional<ProjectSession> findByIdAndUserIdWithCase(@Param("id") UUID id, @Param("userId") UUID userId);
}

interface ProjectTurnRepository extends JpaRepository<ProjectTurn, UUID> {

	List<ProjectTurn> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}

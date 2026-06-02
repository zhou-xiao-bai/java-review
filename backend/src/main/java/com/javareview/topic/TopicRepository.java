package com.javareview.topic;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

	boolean existsByDomainIdAndTitleIgnoreCase(UUID domainId, String title);

	@Query("""
			select topic
			from Topic topic
			join fetch topic.domain domain
			order by domain.sortOrder asc, topic.title asc
			""")
	List<Topic> findAllWithDomain();
}

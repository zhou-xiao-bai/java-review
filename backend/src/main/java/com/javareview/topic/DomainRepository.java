package com.javareview.topic;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainRepository extends JpaRepository<Domain, UUID> {

	List<Domain> findAllByOrderBySortOrderAscNameAsc();
}

package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Request/trace log entries (tenant schema). Read access is Specification-driven
 * (filter engine) — request logs have no associations, so no {@code @EntityGraph} is needed.
 */
@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, UUID>, JpaSpecificationExecutor<RequestLog> {
}
package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Append-only audit log entries (tenant schema). Read access is Specification-driven
 * (filter engine) — audit has no associations, so no {@code @EntityGraph} is needed.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}

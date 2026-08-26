package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Append-only audit log (tenant schema) — Specification-driven reads, no associations to fetch. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}

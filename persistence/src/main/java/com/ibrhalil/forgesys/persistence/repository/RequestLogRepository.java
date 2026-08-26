package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Request/trace log entries (tenant schema) — Specification-driven reads, no associations. */
@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, UUID>, JpaSpecificationExecutor<RequestLog> {
}
package com.ibrhalil.forgesys.persistence.repository;

import java.util.UUID;

import com.ibrhalil.forgesys.entity.PlatformAuditLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Public-schema append-only platform audit repository (K-50). */
@Repository
public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, UUID> {
}

package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    @Query("select a from AuditLog a where a.createdDate >= :from AND a.createdDate < :to")
    Page<AuditLog> findByDateRange(@Param("from") java.time.OffsetDateTime from,
                                   @Param("to") java.time.OffsetDateTime to,
                                   Pageable pageable);
}

package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.AppRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRecordRepository extends JpaRepository<AppRecord, UUID> {

    Page<AppRecord> findAllByAppId(UUID appId, Pageable pageable);

    /** Scoped lookup — a record is only reachable through its owning app. */
    Optional<AppRecord> findByIdAndAppId(UUID id, UUID appId);

    boolean existsByIdAndAppId(UUID id, UUID appId);

    long countByAppId(UUID appId);
}

package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.CustomAppRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomAppRecordRepository extends JpaRepository<CustomAppRecord, UUID> {

    Page<CustomAppRecord> findAllByCustomAppId(UUID customAppId, Pageable pageable);

    /** Scoped lookup — a record is only reachable through its owning custom app. */
    Optional<CustomAppRecord> findByIdAndCustomAppId(UUID id, UUID customAppId);

    boolean existsByIdAndCustomAppId(UUID id, UUID customAppId);

    long countByCustomAppId(UUID customAppId);
}

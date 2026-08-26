package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Append-only login history (tenant schema) — Specification-driven reads, no associations. */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID>, JpaSpecificationExecutor<LoginHistory> {

    /** Most recent failed login for the user (null when none) — user detail activity view. */
    Optional<LoginHistory> findFirstByUserIdAndSuccessFalseOrderByCreatedDateDesc(UUID userId);
}

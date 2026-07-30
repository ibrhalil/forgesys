package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Append-only login attempt history (tenant schema). Read access is
 * Specification-driven (filter engine) — no associations, so no {@code @EntityGraph}.
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID>, JpaSpecificationExecutor<LoginHistory> {

    /**
     * Most recent failed login attempt for the user (null when there has never been
     * one) — backs the user detail activity view. Hits {@code idx_login_history_user_id};
     * the append-only table has no soft-delete rows to filter.
     */
    Optional<LoginHistory> findFirstByUserIdAndSuccessFalseOrderByCreatedDateDesc(UUID userId);
}

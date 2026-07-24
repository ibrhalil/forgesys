package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.TenantVerificationToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TenantVerificationTokenRepository extends JpaRepository<TenantVerificationToken, UUID> {

    Optional<TenantVerificationToken> findByToken(String token);

    Optional<TenantVerificationToken> findByCompanyId(UUID companyId);

    /**
     * [RISK-25] Atomically claims a verification token by stamping {@code used_at} only
     * when it is still {@code NULL}. Returns the affected row count: {@code 1} means the
     * caller won the race and owns the token; {@code 0} means a concurrent verify
     * request already claimed it (or the row vanished between the SELECT and the UPDATE).
     * The caller translates {@code 0} to {@code TENANT_TOKEN_ALREADY_USED}.
     *
     * <p>Conditional UPDATE was chosen over {@code PESSIMISTIC_WRITE} locking because it
     * is portable (H2 test profile + PostgreSQL), needs no lock-timeout tuning, and the
     * claim is a single-column write — no read-modify-write window. Expiry / validity are
     * validated by a separate SELECT before this call so the right
     * {@code TENANT_TOKEN_EXPIRED} / {@code TENANT_TOKEN_INVALID} code is preserved.
     */
    @Modifying
    @Query("UPDATE TenantVerificationToken t SET t.usedAt = :now "
            + "WHERE t.token = :token AND t.usedAt IS NULL")
    int claimToken(@Param("token") String token, @Param("now") OffsetDateTime now);
}

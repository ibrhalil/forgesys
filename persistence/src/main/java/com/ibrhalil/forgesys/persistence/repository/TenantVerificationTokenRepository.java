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

    /**
     * Looks up by the SHA-256 hex digest of the presented raw token ([RISK-30]
     * hash-at-rest) — the caller hashes before querying.
     */
    Optional<TenantVerificationToken> findByToken(String tokenHash);

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
     * The parameter is the token's SHA-256 digest ([RISK-30]), mirroring {@link #findByToken}.
     */
    @Modifying
    @Query("UPDATE TenantVerificationToken t SET t.usedAt = :now "
            + "WHERE t.token = :tokenHash AND t.usedAt IS NULL")
    int claimToken(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /**
     * [RISK-30] Deletes stale tokens: rows consumed ({@code usedAt}) or expired
     * ({@code expiresAt}) before the cutoff. Runs from {@code TokenPurgeJob} daily;
     * keeps the table from growing unbounded. Returns the deleted row count.
     */
    @Modifying
    @Query("DELETE FROM TenantVerificationToken t WHERE t.usedAt < :cutoff OR t.expiresAt < :cutoff")
    int purgeStale(@Param("cutoff") OffsetDateTime cutoff);
}

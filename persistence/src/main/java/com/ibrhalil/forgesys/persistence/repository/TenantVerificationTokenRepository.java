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

    /** Looks up by the SHA-256 digest of the presented raw token (RISK-30) — caller hashes first. */
    Optional<TenantVerificationToken> findByToken(String tokenHash);

    /**
     * Atomically claims the token by stamping {@code used_at} only while NULL
     * (RISK-25): 1 = caller won the race, 0 = already claimed (→
     * {@code TENANT_TOKEN_ALREADY_USED}). Conditional UPDATE instead of
     * {@code PESSIMISTIC_WRITE}: portable across H2+PG, no lock-timeout tuning.
     * Validity/expiry are pre-checked by SELECT so the specific error code survives.
     */
    @Modifying
    @Query("UPDATE TenantVerificationToken t SET t.usedAt = :now "
            + "WHERE t.token = :tokenHash AND t.usedAt IS NULL")
    int claimToken(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /** Deletes used/expired tokens before the cutoff (daily {@code TokenPurgeJob}). */
    @Modifying
    @Query("DELETE FROM TenantVerificationToken t WHERE t.usedAt < :cutoff OR t.expiresAt < :cutoff")
    int purgeStale(@Param("cutoff") OffsetDateTime cutoff);
}

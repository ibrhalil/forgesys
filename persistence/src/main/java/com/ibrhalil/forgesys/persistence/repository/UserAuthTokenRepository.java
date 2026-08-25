package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.UserAuthToken;
import com.ibrhalil.forgesys.entity.UserAuthTokenPurpose;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserAuthTokenRepository extends JpaRepository<UserAuthToken, UUID> {

    /**
     * Looks up by the SHA-256 hex digest of the presented raw token ([RISK-30]
     * hash-at-rest) — the caller hashes before querying.
     */
    Optional<UserAuthToken> findByTokenHash(String tokenHash);

    /**
     * Atomically claims a token by stamping {@code used_at} only when it is still
     * {@code NULL} ([RISK-25] pattern — same rationale as
     * {@code TenantVerificationTokenRepository.claimToken}). Returns 1 when the caller
     * wins the race, 0 when a concurrent consumer already claimed it.
     */
    @Modifying
    @Query("UPDATE UserAuthToken t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL")
    int claimToken(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /**
     * Supersedes the user's outstanding (unused) tokens of a purpose at re-issue —
     * stamps them {@code used_at} so only the newest mailed link stays claimable.
     */
    @Modifying
    @Query("UPDATE UserAuthToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidateOutstanding(@Param("userId") UUID userId,
                              @Param("purpose") UserAuthTokenPurpose purpose,
                              @Param("now") OffsetDateTime now);

    /**
     * [RISK-30] Deletes tokens consumed or expired before the cutoff (daily purge from
     * {@code TokenPurgeJob}). Returns the deleted row count.
     */
    @Modifying
    @Query("DELETE FROM UserAuthToken t WHERE t.usedAt < :cutoff OR t.expiresAt < :cutoff")
    int purgeStale(@Param("cutoff") OffsetDateTime cutoff);
}

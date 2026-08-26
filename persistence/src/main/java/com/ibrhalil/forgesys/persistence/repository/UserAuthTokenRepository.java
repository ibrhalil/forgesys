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

    /** Looks up by the SHA-256 digest of the presented raw token (RISK-30) — caller hashes first. */
    Optional<UserAuthToken> findByTokenHash(String tokenHash);

    /**
     * Atomic claim (RISK-25 pattern, same as
     * {@code TenantVerificationTokenRepository.claimToken}): 1 = won the race,
     * 0 = already claimed.
     */
    @Modifying
    @Query("UPDATE UserAuthToken t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL")
    int claimToken(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /** Supersedes the user's outstanding tokens of a purpose at re-issue — only the newest link stays claimable. */
    @Modifying
    @Query("UPDATE UserAuthToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidateOutstanding(@Param("userId") UUID userId,
                              @Param("purpose") UserAuthTokenPurpose purpose,
                              @Param("now") OffsetDateTime now);

    /** Deletes used/expired tokens before the cutoff (daily {@code TokenPurgeJob}). */
    @Modifying
    @Query("DELETE FROM UserAuthToken t WHERE t.usedAt < :cutoff OR t.expiresAt < :cutoff")
    int purgeStale(@Param("cutoff") OffsetDateTime cutoff);
}

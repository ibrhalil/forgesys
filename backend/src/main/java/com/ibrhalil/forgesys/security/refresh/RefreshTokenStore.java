package com.ibrhalil.forgesys.security.refresh;

import java.util.UUID;

/**
 * Storage abstraction for opaque refresh tokens (K-34). Two profile-bound
 * implementations: {@code RedisRefreshTokenStore} (dev/prod) and
 * {@code InMemoryRefreshTokenStore} (test, Docker-free build).
 *
 * <p>Only the SHA-256 hash of a token is ever persisted — a store/backup leak cannot
 * be replayed. Rotation is atomic and single-use: presenting an already-consumed
 * token is reported as {@link RotationResult.ReuseDetected}.
 *
 * <p>Tenant isolation: a session records its tenant schema; the caller validates the
 * session tenant against the request tenant so a token minted in tenant A cannot be
 * refreshed in tenant B (mirrors [RISK-19] access-token binding).
 */
public interface RefreshTokenStore {

    /** Issues a brand-new refresh token (new session chain) and returns it + metadata. */
    IssuedRefresh issue(UUID userId, String email, String tenant);

    /**
     * Atomically consumes the presented token and issues a new one in the same chain
     * (rotation). An already-consumed token yields {@link RotationResult.ReuseDetected};
     * an absent/expired token yields {@link RotationResult.Unknown}.
     */
    RotationResult rotate(String presentedToken);

    /** Revokes (logs out) a single presented token. Returns {@code true} if it existed. */
    boolean revoke(String presentedToken);

    /** Revokes every refresh token for a user (nuclear — password change/reset, reuse). */
    void revokeAllForUser(UUID userId, String tenant);
}

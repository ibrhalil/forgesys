package com.ibrhalil.forgesys.security;

/**
 * Granular per-{@code jti} access-token revoke (K-34, complementing the user-scoped
 * {@code tokenInvalidBefore}); entries live for the token's remaining lifetime.
 * Profile-split: Redis (dev/prod) / in-memory (test).
 */
public interface TokenBlacklistService {

    /** Adds the JWT id to the blacklist for the given TTL (seconds). */
    void blacklist(String jti, long ttlSeconds);

    /** Returns {@code true} if the JWT id has been blacklisted and has not expired. */
    boolean isBlacklisted(String jti);
}

package com.ibrhalil.forgesys.security;

/**
 * Granular per-token revoke for access tokens (K-34, complementing the user-scoped
 * {@code tokenInvalidBefore}). On per-session logout the current access token's
 * {@code jti} is blacklisted here with a TTL equal to the token's remaining lifetime;
 * {@code JwtAuthenticationFilter} then rejects it (→ 401) without waiting for expiry.
 *
 * <p>Two profile-bound implementations: {@code RedisTokenBlacklistService} (dev/prod)
 * and {@code InMemoryTokenBlacklistService} (test, Docker-free build).
 */
public interface TokenBlacklistService {

    /** Adds the JWT id to the blacklist for the given TTL (seconds). */
    void blacklist(String jti, long ttlSeconds);

    /** Returns {@code true} if the JWT id has been blacklisted and has not expired. */
    boolean isBlacklisted(String jti);
}

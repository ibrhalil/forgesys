package com.ibrhalil.forgesys.security.refresh;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage abstraction for opaque refresh tokens (K-34) and active sessions (K-28).
 * Two profile-bound implementations: {@code RedisRefreshTokenStore} (dev/prod) and
 * {@code InMemoryRefreshTokenStore} (test, Docker-free build).
 *
 * <p>Only the SHA-256 hash of a token is ever persisted — a store/backup leak cannot
 * be replayed. Rotation is atomic and single-use: presenting an already-consumed
 * token is reported as {@link RotationResult.ReuseDetected}.
 *
 * <p>Tenant isolation: a session records its tenant schema; the caller validates the
 * session tenant against the request tenant so a token minted in tenant A cannot be
 * refreshed in tenant B (mirrors [RISK-19] access-token binding).
 *
 * <p>Session management (K-28): each issued session carries a stable {@code sessionId}
 * plus device metadata (IP / User-Agent / loginAt / lastSeen). {@link #listSessions}
 * exposes active sessions for the "where am I logged in" UI; {@link #revokeSession}
 * ends a single session by id (remote revoke); {@link #activeSessionFor} resolves the
 * session behind a presented token so the service can flag the caller's current
 * device. Revoking a session kills its refresh token only — the matching short-lived
 * access token expires at its TTL (the jti is not stored per-session).
 */
public interface RefreshTokenStore {

    /**
     * Issues a brand-new refresh token (new session chain) and returns it + metadata.
     *
     * @param ipAddress client IP captured at login (for the session list), nullable
     * @param userAgent User-Agent captured at login, nullable
     */
    IssuedRefresh issue(UUID userId, String email, String tenant, String ipAddress, String userAgent);

    /**
     * Atomically consumes the presented token and issues a new one in the same chain
     * (rotation). Device metadata and {@code sessionId} are preserved; {@code lastSeen}
     * is bumped. An already-consumed token yields {@link RotationResult.ReuseDetected};
     * an absent/expired token yields {@link RotationResult.Unknown}.
     */
    RotationResult rotate(String presentedToken);

    /** Revokes (logs out) a single presented token. Returns {@code true} if it existed. */
    boolean revoke(String presentedToken);

    /** Revokes every refresh token for a user (nuclear — password change/reset, reuse). */
    void revokeAllForUser(UUID userId, String tenant);

    /**
     * Lists the user's active sessions (K-28), newest login first. Rotated/ended
     * sessions are excluded; only currently usable refresh tokens are returned.
     */
    List<ActiveSession> listSessions(UUID userId, String tenant);

    /**
     * Ends a single session by its stable {@code sessionId} (remote revoke). Returns
     * {@code true} if an active session matched and was removed. The session's refresh
     * token is dropped, so it can no longer rotate; the device's outstanding access
     * token expires at its TTL.
     */
    boolean revokeSession(UUID userId, String tenant, UUID sessionId);

    /**
     * Resolves the active session behind a presented refresh token (used to flag the
     * caller's current device in the session list). Does not consume the token. Empty
     * if the token is unknown/already consumed.
     */
    Optional<ActiveSession> activeSessionFor(String presentedToken);
}

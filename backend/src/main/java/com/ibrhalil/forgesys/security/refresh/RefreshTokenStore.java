package com.ibrhalil.forgesys.security.refresh;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage abstraction for opaque refresh tokens (K-34) and active sessions (K-28).
 * Profile-split: Redis (dev/prod) / in-memory (test). Only the SHA-256 hash of a token
 * is ever persisted; rotation is atomic single-use — presenting a consumed token yields
 * {@link RotationResult.ReuseDetected}.
 */
public interface RefreshTokenStore {

    /** Issues a new refresh token (new session chain); IP/User-Agent feed the session list (nullable). */
    IssuedRefresh issue(UUID userId, String email, String tenant, String ipAddress, String userAgent);

    /**
     * Atomically consumes the token and issues a successor in the same chain (sessionId
     * + device preserved, lastSeen bumped); consumed → ReuseDetected, absent/expired → Unknown.
     */
    RotationResult rotate(String presentedToken);

    /** Revokes (logs out) a single presented token. Returns {@code true} if it existed. */
    boolean revoke(String presentedToken);

    /** Revokes every refresh token for a user (password change/reset, reuse detection). */
    void revokeAllForUser(UUID userId, String tenant);

    /** The user's active sessions (newest activity first; only currently usable tokens). */
    List<ActiveSession> listSessions(UUID userId, String tenant);

    /** Every active session in the tenant (admin view) — enumerates per-user indexes and aggregates {@link #listSessions(UUID, String)}. */
    List<ActiveSession> listAllSessions(String tenant);

    /** Ends one session by id (drops its refresh token); the service also stamps {@code tokenInvalidBefore}. */
    boolean revokeSession(UUID userId, String tenant, UUID sessionId);

    /** Session behind a presented token WITHOUT consuming it; empty if unknown/consumed. */
    Optional<ActiveSession> activeSessionFor(String presentedToken);
}

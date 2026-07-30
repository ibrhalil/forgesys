package com.ibrhalil.forgesys.security.refresh;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projection of an active refresh-token session (K-28 session management) returned by
 * {@link RefreshTokenStore#listSessions(UUID, String)}. Unlike the lean
 * {@link RefreshSession} (which carries only what {@code AuthService} needs to
 * re-resolve a user on rotation), this carries the device metadata captured at issue
 * time so the UI can render a "where you're logged in" list.
 *
 * <p>A session corresponds to a single device/browser. Rotation (token refresh)
 * mints a new token hash but preserves the stable {@code sessionId} and original
 * device metadata, bumping {@code lastSeen}. The {@code current} flag is NOT set by
 * the store; the service layer marks the session matching the caller's presented
 * refresh token.
 *
 * @param sessionId  stable per-device identifier (preserved across rotation)
 * @param userId     owner of the session
 * @param email      login identifier
 * @param tenant     tenant schema the session is bound to
 * @param ipAddress  client IP captured at login (may be null in tests / unknown)
 * @param userAgent  User-Agent captured at login (may be null)
 * @param loginAt    when the session was first issued (preserved across rotation)
 * @param lastSeen   most recent rotation instant (equals {@code loginAt} until rotated)
 */
public record ActiveSession(
        UUID sessionId,
        UUID userId,
        String email,
        String tenant,
        String ipAddress,
        String userAgent,
        OffsetDateTime loginAt,
        OffsetDateTime lastSeen
) {
}

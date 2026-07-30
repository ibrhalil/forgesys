package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Active session exposed by {@code GET /api/v1/users/me/sessions} and the admin
 * {@code GET /api/v1/users/{id}/sessions} (K-28). Mirrors one usable refresh-token
 * session (a single device/browser). Rotation preserves {@code sessionId} and the
 * original device metadata while advancing {@code lastSeen}.
 *
 * <p>The {@code current} flag marks the session behind the caller's presented refresh
 * token so the UI can label "this device" and warn before self-revoke. It is always
 * {@code false} on the admin view (the admin is not the session owner).
 *
 * @param sessionId stable per-device identifier
 * @param userAgent User-Agent captured at login, or null
 * @param ipAddress client IP captured at login, or null
 * @param loginAt   when the session was first issued
 * @param lastSeen  most recent rotation instant
 * @param current   whether this is the caller's own current session
 */
public record ActiveSessionResponse(
        UUID sessionId,
        String userAgent,
        String ipAddress,
        OffsetDateTime loginAt,
        OffsetDateTime lastSeen,
        boolean current
) {
}

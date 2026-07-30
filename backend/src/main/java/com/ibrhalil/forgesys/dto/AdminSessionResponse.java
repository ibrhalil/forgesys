package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Active session with its <em>owner</em>, exposed by the admin
 * {@code GET /api/v1/sessions} (tenant-wide "all sessions" view). Unlike
 * {@link ActiveSessionResponse} (the self / per-user view, which omits the owner), this
 * carries {@code userId} + {@code email} so the admin table can show <em>who</em> each
 * session belongs to. {@code current} is always absent here (the admin is not each
 * session's owner); a caller's own session is identifiable client-side via the user id.
 *
 * @param sessionId stable per-device identifier
 * @param userId    owner of the session
 * @param email     login identifier of the owner
 * @param userAgent User-Agent captured at login, or null
 * @param ipAddress client IP captured at login, or null
 * @param loginAt   when the session was first issued
 * @param lastSeen  most recent rotation instant
 */
public record AdminSessionResponse(
        UUID sessionId,
        UUID userId,
        String email,
        String userAgent,
        String ipAddress,
        OffsetDateTime loginAt,
        OffsetDateTime lastSeen
) {
}

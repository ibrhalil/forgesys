package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin tenant-wide session view — like {@link ActiveSessionResponse} but carrying
 * the owner ({@code userId} + {@code email}); no {@code current} flag (the admin is
 * not the session owner).
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

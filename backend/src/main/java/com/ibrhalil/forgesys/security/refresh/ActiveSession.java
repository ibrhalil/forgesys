package com.ibrhalil.forgesys.security.refresh;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Active session projection for the "where you're logged in" UI (K-28). One session =
 * one device; rotation preserves {@code sessionId}/{@code loginAt} and bumps
 * {@code lastSeen}. The {@code current} flag is set by the service layer, not the store.
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

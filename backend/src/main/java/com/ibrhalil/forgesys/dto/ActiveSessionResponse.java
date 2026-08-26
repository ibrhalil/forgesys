package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One usable refresh-token session (K-28): rotation preserves {@code sessionId}
 * and the login-time device metadata while advancing {@code lastSeen}.
 * {@code current} marks the caller's own session — always {@code false} on the
 * admin view.
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

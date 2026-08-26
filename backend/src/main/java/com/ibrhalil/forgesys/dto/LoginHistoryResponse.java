package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Login-history row for {@code GET /api/v1/login-history} (K-19 layer 2);
 * {@code reason} is the stable {@code ErrorCode} wire value, null on success.
 */
public record LoginHistoryResponse(
        UUID id,
        UUID userId,
        String username,
        boolean success,
        String reason,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt
) {
}

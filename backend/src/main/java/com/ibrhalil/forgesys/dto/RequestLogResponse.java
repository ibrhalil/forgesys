package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request-log row for {@code GET /api/v1/request-logs} (K-19 layer 3 + K-27);
 * {@code requestBody} is the masked body (high-risk paths only), null otherwise.
 */
public record RequestLogResponse(
        UUID id,
        String traceId,
        String method,
        String path,
        Integer status,
        Long durationMs,
        UUID userId,
        String username,
        String ipAddress,
        String userAgent,
        String requestBody,
        OffsetDateTime createdAt
) {
}
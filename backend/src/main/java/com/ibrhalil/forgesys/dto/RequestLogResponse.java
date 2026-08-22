package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant request/trace log entry exposed by {@code GET /api/v1/request-logs}.
 * Mirrors the {@code t_request_logs} record (K-19 layer 3 + K-27).
 *
 * @param id           request-log id
 * @param traceId      per-request trace id
 * @param method       HTTP method
 * @param path         request path
 * @param status       HTTP response status
 * @param durationMs   request processing time in milliseconds
 * @param userId       the authenticated user's id, or null
 * @param username     the authenticated user's email, or null
 * @param ipAddress    resolved client IP
 * @param userAgent    User-Agent header (truncated)
 * @param requestBody  masked request body (high-risk paths only), or null
 * @param createdAt    when the request was recorded
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
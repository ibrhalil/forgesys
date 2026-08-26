package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit-log row for {@code GET /api/v1/audit-logs} (K-19 layer 1); excludes the
 * K-27 delta bodies ({@code oldValue}/{@code newValue}/{@code requestBody}).
 */
public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        String entityName,
        String ipAddress,
        String traceId,
        OffsetDateTime createdAt
) {
}

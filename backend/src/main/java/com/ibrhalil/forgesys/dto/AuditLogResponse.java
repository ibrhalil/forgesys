package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant audit-log entry exposed by {@code GET /api/v1/audit-logs}. Mirrors the
 * {@code t_audit_logs} record (K-19 layer 1) minus the high-risk JSONB bodies
 * ({@code oldValue}/{@code newValue}/{@code requestBody}), which are added with
 * K-27 high-risk body capture.
 *
 * @param id         audit-log id
 * @param actorId    the acting user's id, or null when no principal was present (system)
 * @param actorName  the acting user's email, or {@code "system"}
 * @param action     stable action key (e.g. {@code user_created})
 * @param entityType entity class label (e.g. {@code User})
 * @param entityId   the affected entity's id
 * @param entityName human-readable entity label (email / name) for the activity feed
 * @param ipAddress  resolved client IP
 * @param traceId    per-request trace id
 * @param createdAt  when the action was recorded
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

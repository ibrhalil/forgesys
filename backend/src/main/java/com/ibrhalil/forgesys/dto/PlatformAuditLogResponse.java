package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model of a {@code t_platform_audit_logs} row (K-50 F7 platform console). */
public record PlatformAuditLogResponse(
        UUID id,
        UUID actorId,
        String actorType,
        String action,
        String targetType,
        UUID targetId,
        String detail,
        String ipAddress,
        String traceId,
        OffsetDateTime createdAt) {
}

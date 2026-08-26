package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A custom app list row (K-15) — definition only; carries the APPS container
 * id/name (K-45) for the project chip.
 */
public record AppResponse(
        UUID id,
        String name,
        String description,
        String icon,
        UUID projectId,
        String projectName,
        OffsetDateTime createdDate,
        OffsetDateTime updatedAt) {
}

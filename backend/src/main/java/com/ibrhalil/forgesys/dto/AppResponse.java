package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A custom app row for lists (K-15 / Epic 3.0.B) — definition only; properties/views
 * live in {@link AppDetailResponse}. Carries the APPS container id/name (K-45) so
 * list rows render the project chip without a second round-trip (batched server-side).
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

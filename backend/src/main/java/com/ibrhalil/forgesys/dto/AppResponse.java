package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A custom app row for lists (K-15 / Epic 3.0.B) — definition only; properties/views
 * live in {@link AppDetailResponse}.
 */
public record AppResponse(
        UUID id,
        String name,
        String description,
        String icon,
        OffsetDateTime createdDate,
        OffsetDateTime updatedAt) {
}

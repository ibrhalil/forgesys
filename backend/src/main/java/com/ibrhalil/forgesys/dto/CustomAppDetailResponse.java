package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A custom app with its full definition (properties + views, stable order) for
 * {@code GET /apps/{id}}; carries the APPS container id/name (K-45).
 */
public record CustomAppDetailResponse(
        UUID id,
        String name,
        String description,
        String icon,
        UUID projectId,
        String projectName,
        OffsetDateTime createdDate,
        OffsetDateTime updatedAt,
        List<CustomAppPropertyResponse> properties,
        List<CustomAppViewResponse> views) {
}

package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

/**
 * A custom app with its full definition (K-15 / Epic 3.0.B): properties (columns) and
 * views, each in stable order. Returned by {@code GET /api/v1/apps/{id}}.
 */
public record AppDetailResponse(
        UUID id,
        String name,
        String description,
        String icon,
        List<AppPropertyResponse> properties,
        List<AppViewResponse> views) {
}

package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.PropertyType;

import java.util.UUID;

/**
 * A property (column) definition of a custom app (K-15 / Epic 3.0.B).
 */
public record AppPropertyResponse(
        UUID id,
        UUID appId,
        String name,
        PropertyType type,
        AppPropertyConfigDto config,
        boolean required,
        int position) {
}

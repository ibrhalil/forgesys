package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.PropertyType;

import java.util.UUID;

/**
 * A property (column) definition of a custom app (K-15).
 */
public record CustomAppPropertyResponse(
        UUID id,
        UUID customAppId,
        String name,
        PropertyType type,
        CustomAppPropertyConfigDto config,
        boolean required,
        int position) {
}

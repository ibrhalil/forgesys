package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ViewType;

import java.util.UUID;

/**
 * A saved view of a custom app (K-15).
 */
public record AppViewResponse(
        UUID id,
        UUID appId,
        String name,
        ViewType type,
        AppViewConfigDto config,
        int position) {
}

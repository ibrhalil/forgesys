package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ViewType;

import java.util.UUID;

/**
 * A saved view of a custom app (K-15).
 */
public record CustomAppViewResponse(
        UUID id,
        UUID customAppId,
        String name,
        ViewType type,
        CustomAppViewConfigDto config,
        int position) {
}

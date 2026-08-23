package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ProjectType;

import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectType type,
        UUID parentProjectId,
        boolean isDefault
) {
}

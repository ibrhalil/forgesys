package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ProjectType;

import java.util.UUID;

/**
 * One entry of the creatable project-type catalog (K-45): type, supplying module
 * key, and the per-type default container id when one exists.
 */
public record ProjectTypeResponse(
        ProjectType type,
        String moduleKey,
        UUID defaultProjectId
) {
}

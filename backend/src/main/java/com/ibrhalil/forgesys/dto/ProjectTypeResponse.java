package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ProjectType;

import java.util.UUID;

/**
 * One entry of the creatable project-type catalog (K-45): the type, the module that
 * supplies its content (key of {@code ModuleDefinition}), and the per-type default
 * container's id when one exists (the top-nav fallback target for module content).
 */
public record ProjectTypeResponse(
        ProjectType type,
        String moduleKey,
        UUID defaultProjectId
) {
}

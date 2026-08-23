package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update a typed project container (K-45). The {@link ProjectType} decides which
 * built-in module's content lives inside the project; it is required (every project has
 * a kind). {@code parentProjectId} adds optional nesting (validated for existence and
 * acyclicity; {@code null} detaches).
 */
public record ProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(max = 150, message = "Project name must be at most 150 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Project type is required")
        ProjectType type,

        UUID parentProjectId
) {
}

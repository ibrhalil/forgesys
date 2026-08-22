package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update a custom app (K-15 / Epic 3.0.B).
 */
public record AppRequest(
        @NotBlank(message = "App name is required")
        @Size(max = 150, message = "App name must be at most 150 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Size(max = 50, message = "Icon must be at most 50 characters")
        String icon
) {
}

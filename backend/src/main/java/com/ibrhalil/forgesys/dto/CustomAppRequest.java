package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update a custom app (K-15); absent {@code projectId} on create lands in
 * the default APPS container, on update {@code null} keeps and a value moves the
 * app between APPS containers (K-45).
 */
public record CustomAppRequest(
        @NotBlank(message = "Custom app name is required")
        @Size(max = 150, message = "Custom app name must be at most 150 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Size(max = 50, message = "Icon must be at most 50 characters")
        String icon,

        UUID projectId
) {
}

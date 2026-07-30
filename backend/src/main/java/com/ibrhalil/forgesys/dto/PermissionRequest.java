package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a tenant permission. The name MUST follow the
 * {@code {module}:{resource}:{action}} convention enforced across the built-in
 * {@link com.ibrhalil.forgesys.config.PermissionCatalog} so runtime-created permissions
 * stay consistent with the seeded namespace.
 */
public record PermissionRequest(
        @NotBlank(message = "Permission name is required")
        @Size(max = 100, message = "Permission name must be at most 100 characters")
        @Pattern(regexp = "^[a-z][a-z0-9]*:[a-z][a-z0-9-]*:[a-z]+$",
                message = "Permission name must follow '{module}:{resource}:{action}' (lowercase)")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description
) {
}

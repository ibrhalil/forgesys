package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ViewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update a view of a custom app (K-15 / Epic 3.0.B). {@code config} is validated
 * against the app's property set and stored as canonical JSON. {@code position} is
 * optional: absent on create appends at the end (max+1), absent on update keeps the
 * current value.
 */
public record AppViewRequest(
        @NotBlank(message = "View name is required")
        @Size(max = 150, message = "View name must be at most 150 characters")
        String name,

        @NotNull(message = "View type is required")
        ViewType type,

        AppViewConfigDto config,

        @Min(0)
        @Max(9999)
        Integer position
) {
}

package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.ViewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update a view of a custom app (K-15); {@code config} is validated
 * against the app's property set. {@code position} absent appends on create /
 * keeps current on update.
 */
public record CustomAppViewRequest(
        @NotBlank(message = "View name is required")
        @Size(max = 150, message = "View name must be at most 150 characters")
        String name,

        @NotNull(message = "View type is required")
        ViewType type,

        CustomAppViewConfigDto config,

        @Min(0)
        @Max(9999)
        Integer position
) {
}

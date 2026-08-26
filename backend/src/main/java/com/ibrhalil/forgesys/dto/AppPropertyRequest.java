package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.PropertyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update a property (column) of a custom app (K-15). {@code type} is
 * immutable after creation; {@code position} absent appends on create / keeps
 * current on update. The {@code required} wrapper + compact-constructor default
 * exists because Jackson 3 fails null-into-primitive mapping for absent fields.
 */
public record AppPropertyRequest(
        @NotBlank(message = "Property name is required")
        @Size(max = 150, message = "Property name must be at most 150 characters")
        String name,

        @NotNull(message = "Property type is required")
        PropertyType type,

        AppPropertyConfigDto config,

        Boolean required,

        @Min(0)
        @Max(9999)
        Integer position
) {

    public AppPropertyRequest {
        required = required == null ? Boolean.FALSE : required;
    }
}

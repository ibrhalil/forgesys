package com.ibrhalil.systemforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description
) {
}

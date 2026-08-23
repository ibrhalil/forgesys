package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update a note category. {@code color} is an optional UI token
 * (hex string or a Tailwind class fragment) — never interpreted server-side.
 */
public record NoteCategoryRequest(
        @NotBlank(message = "Note category name is required")
        @Size(max = 100, message = "Note category name must be at most 100 characters")
        String name,

        @Size(max = 20, message = "Color must be at most 20 characters")
        String color
) {
}

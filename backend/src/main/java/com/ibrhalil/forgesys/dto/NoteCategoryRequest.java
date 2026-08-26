package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update a note category; {@code color} is a UI token never interpreted
 * server-side. Absent {@code projectId} on create lands in the default NOTES
 * container; on update a different project is rejected (categories do not move).
 */
public record NoteCategoryRequest(
        @NotBlank(message = "Note category name is required")
        @Size(max = 100, message = "Note category name must be at most 100 characters")
        String name,

        @Size(max = 20, message = "Color must be at most 20 characters")
        String color,

        UUID projectId
) {
}

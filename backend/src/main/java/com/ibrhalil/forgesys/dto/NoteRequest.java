package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update a note. Content is markdown text and may be empty (title-only note).
 * {@code categoryId} is optional; {@code pinned} defaults to {@code false} on create
 * and {@code null} means "leave unchanged" on update.
 */
public record NoteRequest(
        @NotBlank(message = "Note title is required")
        @Size(max = 200, message = "Note title must be at most 200 characters")
        String title,

        @Size(max = 100_000, message = "Note content must be at most 100000 characters")
        String content,

        UUID categoryId,

        Boolean pinned
) {
}

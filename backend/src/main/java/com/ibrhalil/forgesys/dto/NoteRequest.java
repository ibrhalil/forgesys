package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create/update a note; content is markdown and may be empty. On update,
 * {@code null} {@code pinned}/{@code projectId} means "leave unchanged"; a
 * {@code projectId} value moves the note. On create, absent {@code projectId}
 * lands in the default NOTES container (K-45).
 */
public record NoteRequest(
        @NotBlank(message = "Note title is required")
        @Size(max = 200, message = "Note title must be at most 200 characters")
        String title,

        @Size(max = 100_000, message = "Note content must be at most 100000 characters")
        String content,

        UUID categoryId,

        Boolean pinned,

        UUID projectId
) {
}

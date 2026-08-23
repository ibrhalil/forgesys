package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Note view. Carries the category name alongside the id so list rows can render the
 * chip without a second round-trip (the join-free lookup happens server-side).
 */
public record NoteResponse(
        UUID id,
        String title,
        String content,
        UUID categoryId,
        String categoryName,
        boolean pinned,
        OffsetDateTime updatedAt
) {
}

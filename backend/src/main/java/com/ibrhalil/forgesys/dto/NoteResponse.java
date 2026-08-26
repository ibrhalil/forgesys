package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Note view carrying container/category names so list rows render chips without
 * a second round-trip (batched server-side, K-45).
 */
public record NoteResponse(
        UUID id,
        String title,
        String content,
        UUID projectId,
        String projectName,
        UUID categoryId,
        String categoryName,
        boolean pinned,
        OffsetDateTime updatedAt
) {
}

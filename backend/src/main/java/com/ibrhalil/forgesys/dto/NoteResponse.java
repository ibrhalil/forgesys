package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Note view. Carries the container/project and category names alongside the ids so
 * list rows can render chips without a second round-trip (the join-free lookups
 * happen server-side, batched per page — K-45).
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

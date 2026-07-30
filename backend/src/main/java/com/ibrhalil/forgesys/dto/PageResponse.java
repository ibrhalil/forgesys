package com.ibrhalil.forgesys.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform envelope for every paged list endpoint: {@code data[] + meta}. Replaces the
 * raw Spring Data {@code Page<T>} serialization so the wire contract is owned by the
 * API rather than the framework (Boot's flat/nested Page layout drift) — the same
 * philosophy as {@code ApiErrorResponse}/{@code ErrorCode}. {@code meta.page} is
 * 0-based, consistent with the previous Spring Data wire format.
 */
public record PageResponse<T>(List<T> data, PageMetadata meta) {

    public record PageMetadata(
            int page,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious) {
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()));
    }
}

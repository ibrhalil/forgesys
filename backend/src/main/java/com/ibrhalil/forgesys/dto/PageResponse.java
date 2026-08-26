package com.ibrhalil.forgesys.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform {@code data[] + meta} envelope for every paged list — the API owns the
 * wire contract instead of Spring Data's {@code Page} serialization.
 * {@code meta.page} is 0-based.
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

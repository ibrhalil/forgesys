package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of the {@code POST /{resource}/search} list endpoints: paging + multi-sort +
 * structured filters + global {@code q} search (optionally narrowed to
 * {@code qFields} — smart-search field targeting; unknown or non-searchable names
 * fail with 400) in one request. The simpler GET lists (query-param {@code q} +
 * Spring Data sort) stay for bookmarkable reads; this shape is the forward contract
 * for richer clients. Hard limits (filter count, sort count, values-per-filter,
 * page size ≤ 1000 — aligned with {@code spring.data.web.pageable.max-page-size})
 * keep requests bounded; a bounded request is a cheap request.
 */
public record SearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(1000) Integer size,
        @Size(max = 5) List<SortCriteria> sorts,
        @Size(max = 10) List<FilterCriteria> filters,
        @Size(max = 200) String q,
        @Size(max = 10) List<String> qFields) {
}

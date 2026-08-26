package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of the {@code POST /{resource}/search} endpoints (K-49): paging +
 * multi-sort + structured filters + {@code q} (optionally narrowed to
 * {@code qFields}). Hard limits keep requests bounded: ≤5 sorts, ≤10 filters,
 * size ≤1000.
 */
public record SearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(1000) Integer size,
        @Size(max = 5) List<SortCriteria> sorts,
        @Size(max = 10) List<FilterCriteria> filters,
        @Size(max = 200) String q,
        @Size(max = 10) List<String> qFields) {
}

package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/apps/{appId}/records/search} (K-15 / Epic 3.0.B):
 * property-value filters/sorts + paging, executed against the JSONB EAV store
 * (PostgreSQL {@code @>} containment / {@code #>>} text accessors, GIN-backed).
 * Limits mirror the generic filter engine: ≤10 filters, ≤5 sorts, page size ≤100
 * (value scans are heavier than column reads, so the page cap is tighter).
 */
public record AppRecordSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 5) List<AppValueSortCriteria> sorts,
        @Size(max = 10) List<AppValueFilterCriteria> filters) {
}

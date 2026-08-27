package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /apps/{customAppId}/records/search} (K-15): property-value
 * filters/sorts executed against the JSONB EAV store (PostgreSQL-only). Limits:
 * ≤10 filters, ≤5 sorts, size ≤100 (value scans are heavier than column reads).
 */
public record CustomAppRecordSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 5) List<CustomAppValueSortCriteria> sorts,
        @Size(max = 10) List<CustomAppValueFilterCriteria> filters) {
}

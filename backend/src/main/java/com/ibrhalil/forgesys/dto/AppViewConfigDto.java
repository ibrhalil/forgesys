package com.ibrhalil.forgesys.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Structured config of a custom app view (K-15): property-referencing
 * filters/sorts plus grouping anchors ({@code groupBy} for BOARD,
 * {@code dateProperty} for CALENDAR). Deliberately NOT a free-text expression
 * language — every field is a property id or enum, re-validated against the
 * app's property set (no injection surface).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppViewConfigDto(
        @Size(max = 10) List<AppValueFilterCriteria> filters,
        @Size(max = 5) List<AppValueSortCriteria> sorts,
        String groupBy,
        String dateProperty) {
}

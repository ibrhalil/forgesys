package com.ibrhalil.forgesys.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Structured config of a custom app view (K-15 / Epic 3.0.B): property-referencing
 * filters/sorts plus the view-type-specific grouping anchors ({@code groupBy} for
 * BOARD, {@code dateProperty} for CALENDAR). Deliberately a <em>structured</em> shape —
 * not a free-text expression language — so the injection surface is structural (the
 * 3.0.B spike outcome): every field resolves to a property id or an enum, and the
 * backend re-validates against the app's property set before persisting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppViewConfigDto(
        @Size(max = 10) List<AppValueFilterCriteria> filters,
        @Size(max = 5) List<AppValueSortCriteria> sorts,
        String groupBy,
        String dateProperty) {
}

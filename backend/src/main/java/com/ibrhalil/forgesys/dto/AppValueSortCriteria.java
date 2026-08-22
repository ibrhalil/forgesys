package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

/**
 * One sort clause over a custom app property value (K-15 / Epic 3.0.B).
 * {@code propertyId} is either a property id of the app or the reserved
 * {@code createdAt} (record creation time). {@code direction} defaults to {@code asc}
 * (same normalization as {@link SortCriteria}).
 */
public record AppValueSortCriteria(
        @NotBlank String propertyId,
        @Pattern(regexp = "(?i)^(asc|desc)$", message = "must be 'asc' or 'desc'") String direction) {

    public AppValueSortCriteria {
        direction = direction == null || direction.isBlank() ? "asc" : direction.toLowerCase(Locale.ROOT);
    }

    public boolean descending() {
        return "desc".equals(direction);
    }
}

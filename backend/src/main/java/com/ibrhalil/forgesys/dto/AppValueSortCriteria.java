package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

/**
 * One sort clause over a custom app property value (K-15); {@code propertyId}
 * is a property id or the reserved {@code createdAt}. Direction defaults to asc.
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

package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One sort clause of a {@link SearchRequest}. {@code direction} is optional
 * (default {@code asc}); field names are validated against the feature's
 * {@code FilterFieldSet} (same whitelist as sorting the GET lists).
 */
public record SortCriteria(
        @NotBlank String field,
        @Pattern(regexp = "(?i)^(asc|desc)$", message = "must be 'asc' or 'desc'") String direction) {

    public SortCriteria {
        direction = direction == null || direction.isBlank() ? "asc" : direction.toLowerCase(java.util.Locale.ROOT);
    }
}

package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * One filter clause over a custom app property value (K-15 / Epic 3.0.B): references a
 * property by id, a value {@link AppValueOperator}, and a raw JSON {@code value}
 * (absent for IS_EMPTY / IS_NOT_EMPTY). Used by both record search
 * ({@code AppRecordSearchRequest}) and saved view configs ({@code AppViewConfigDto}) —
 * one wire shape, validated by {@code AppQueryValidator}.
 */
public record AppValueFilterCriteria(
        @NotBlank String propertyId,
        @NotNull AppValueOperator operator,
        JsonNode value) {
}

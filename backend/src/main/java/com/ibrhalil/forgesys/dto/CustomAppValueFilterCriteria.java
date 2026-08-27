package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * One filter clause over a custom app property value (K-15): property id +
 * operator + raw JSON {@code value} (absent for IS_EMPTY / IS_NOT_EMPTY).
 */
public record CustomAppValueFilterCriteria(
        @NotBlank String propertyId,
        @NotNull CustomAppValueOperator operator,
        JsonNode value) {
}

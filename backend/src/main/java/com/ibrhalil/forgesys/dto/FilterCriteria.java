package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.web.filter.FilterOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One filter clause of a {@link SearchRequest}. {@code values} are wire strings parsed
 * by the filter engine per the field's registered type (UUID / ISO-8601 / boolean /
 * enum name / raw string); a parse failure is a 400 {@code validation_error} naming the
 * field. Operator-specific arity (e.g. BETWEEN = exactly 2, IN = 1..100) is enforced
 * by the engine.
 */
public record FilterCriteria(
        @NotBlank String field,
        @NotNull FilterOperator operator,
        @Size(max = 100) List<String> values) {
}

package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Parses a wire string into the typed value a filter predicate needs, per the field's
 * registered {@link FilterFieldType}. Every parse failure is a 400
 * {@code validation_error} naming the field — an unparseable value must never surface
 * as a 500 or silently match nothing ([RISK-29] semantics).
 */
final class FilterValueParser {

    private FilterValueParser() {
    }

    static Object parse(FilterFieldSet.RegisteredField field, String raw) {
        try {
            return switch (field.type()) {
                case STRING -> raw;
                case UUID -> UUID.fromString(raw.trim());
                case BOOLEAN -> parseBoolean(field, raw);
                case TEMPORAL -> OffsetDateTime.parse(raw.trim());
                case NUMERIC -> Long.parseLong(raw.trim());
                case ENUM -> parseEnum(field, raw);
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid(field, raw, e.getMessage());
        }
    }

    private static Object parseBoolean(FilterFieldSet.RegisteredField field, String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return Boolean.valueOf(normalized);
        }
        throw invalid(field, raw, "expected 'true' or 'false'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(FilterFieldSet.RegisteredField field, String raw) {
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.javaType();
        return Enum.valueOf(enumType, raw.trim());
    }

    private static BusinessException invalid(FilterFieldSet.RegisteredField field, String raw, String detail) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Invalid value for filter '" + field.name() + "' (" + field.type() + "): '" + raw + "' — " + detail);
    }
}

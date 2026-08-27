package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Validates a raw JSON cell value against its {@link PropertyType} definition (K-15).
 * Reference-typed values (USER/RELATION) get an existence check against tenant data —
 * the JSONB column cannot carry an FK (same rationale as {@code Task.assigneeId}).
 * Rationale: docs/CODE_NOTES.md (backend/service → CustomAppPropertyValueValidator).
 */
@Component
@RequiredArgsConstructor
public class CustomAppPropertyValueValidator {

    /** Upper bound for TEXT cells — keeps JSONB rows small and rendering cheap. */
    public static final int MAX_TEXT_LENGTH = 5000;

    private final UserRepository userRepository;
    private final CustomAppRepository customAppRepository;
    private final CustomAppRecordRepository customAppRecordRepository;
    private final ObjectMapper objectMapper;

    /** Throws {@link ErrorCode#CUSTOM_APP_RECORD_VALUE_INVALID} on any mismatch. */
    public void validate(CustomAppProperty property, JsonNode value) {
        switch (property.getType()) {
            case TEXT -> validateText(property, value);
            case NUMBER -> validateNumber(property, value);
            case SELECT -> validateSelect(property, value);
            case DATE -> validateDate(property, value);
            case USER -> validateUser(property, value);
            case RELATION -> validateRelation(property, value);
            case FORMULA -> invalid(property, "FORMULA properties cannot be set directly (deferred type)");
        }
    }

    private void validateText(CustomAppProperty property, JsonNode value) {
        if (!value.isTextual()) {
            invalid(property, "expected a string");
        }
        String text = value.stringValue();
        if (text.length() > MAX_TEXT_LENGTH) {
            invalid(property, "text longer than " + MAX_TEXT_LENGTH + " characters");
        }
    }

    private void validateNumber(CustomAppProperty property, JsonNode value) {
        if (!value.isNumber()) {
            invalid(property, "expected a number");
        }
        double asDouble = value.doubleValue();
        if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
            invalid(property, "number must be finite");
        }
    }

    private void validateSelect(CustomAppProperty property, JsonNode value) {
        if (!value.isTextual()) {
            invalid(property, "expected one of the configured options (string)");
        }
        String option = value.stringValue();
        if (!options(property).contains(option)) {
            invalid(property, "'" + option + "' is not a configured option");
        }
    }

    private void validateDate(CustomAppProperty property, JsonNode value) {
        if (!value.isTextual()) {
            invalid(property, "expected an ISO-8601 date string (YYYY-MM-DD)");
        }
        try {
            LocalDate.parse(value.stringValue());
        } catch (DateTimeParseException ex) {
            invalid(property, "expected an ISO-8601 date string (YYYY-MM-DD)");
        }
    }

    private void validateUser(CustomAppProperty property, JsonNode value) {
        UUID userId = parseUuid(property, value);
        if (!userRepository.existsById(userId)) {
            invalid(property, "user '" + userId + "' does not exist in this tenant");
        }
    }

    private void validateRelation(CustomAppProperty property, JsonNode value) {
        UUID targetCustomAppId = targetCustomAppId(property);
        UUID recordId = parseUuid(property, value);
        if (!customAppRecordRepository.existsByIdAndCustomAppId(recordId, targetCustomAppId)) {
            invalid(property, "record '" + recordId + "' does not exist in the related app");
        }
    }

    private UUID parseUuid(CustomAppProperty property, JsonNode value) {
        if (!value.isTextual()) {
            invalid(property, "expected a UUID string");
        }
        try {
            return UUID.fromString(value.stringValue());
        } catch (IllegalArgumentException ex) {
            invalid(property, "expected a UUID string");
            return null; // unreachable — invalid() always throws
        }
    }

    /** Configured SELECT options; a config-less SELECT was never creatable, so this is a guard. */
    private java.util.Set<String> options(CustomAppProperty property) {
        JsonNode config = parseConfig(property);
        JsonNode options = config == null ? null : config.get("options");
        if (options == null || !options.isArray() || options.isEmpty()) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                    "Property '" + property.getName() + "' has no configured options");
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode option : options) {
            names.add(option.stringValue());
        }
        return names;
    }

    private UUID targetCustomAppId(CustomAppProperty property) {
        JsonNode config = parseConfig(property);
        JsonNode target = config == null ? null : config.get("targetCustomAppId");
        if (target == null || !target.isTextual()) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                    "Property '" + property.getName() + "' has no target app configured");
        }
        // Definitions can drift (target hard-purged) — a dangling relation must fail loudly.
        UUID targetCustomAppId = UUID.fromString(target.stringValue());
        if (!customAppRepository.existsById(targetCustomAppId)) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID,
                    "Property '" + property.getName() + "' points at a nonexistent app");
        }
        return targetCustomAppId;
    }

    private JsonNode parseConfig(CustomAppProperty property) {
        String config = property.getConfig();
        if (config == null || config.isBlank()) {
            return null;
        }
        return objectMapper.readTree(config);
    }

    private void invalid(CustomAppProperty property, String reason) {
        throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                "Invalid value for property '%s' (%s): %s".formatted(property.getName(), property.getType(), reason));
    }
}

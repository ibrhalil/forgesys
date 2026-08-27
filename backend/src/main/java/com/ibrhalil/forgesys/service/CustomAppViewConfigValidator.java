package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppViewConfigDto;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Validates a view config against the app's property set and serializes the canonical
 * JSON for {@code t_custom_app_views.config} (K-15). Filters/sorts delegated to
 * {@link CustomAppQueryValidator}; this class owns the type anchors: BOARD requires a
 * {@code groupBy} (SELECT), CALENDAR a {@code dateProperty} (DATE). Structured JSON
 * only — no expression language, no injection surface.
 */
@Component
@RequiredArgsConstructor
public class CustomAppViewConfigValidator {

    private final CustomAppQueryValidator customAppQueryValidator;
    private final ObjectMapper objectMapper;

    /**
     * @return canonical JSON to persist ({@code null} for an absent config). Required
     * anchors are enforced even when the request carries no config object at all.
     */
    public String validateAndSerialize(ViewType viewType, CustomAppViewConfigDto config,
                                       Map<UUID, CustomAppProperty> properties) {
        CustomAppViewConfigDto effective = config == null
                ? new CustomAppViewConfigDto(null, null, null, null)
                : config;
        customAppQueryValidator.validateFilters(effective.filters(), properties, ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
        customAppQueryValidator.validateSorts(effective.sorts(), properties, ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID);
        validateTypedAnchors(viewType, effective, properties);
        return config == null ? null : objectMapper.writeValueAsString(config);
    }

    private void validateTypedAnchors(ViewType viewType, CustomAppViewConfigDto config,
                                      Map<UUID, CustomAppProperty> properties) {
        switch (viewType) {
            case BOARD -> requireAnchor("groupBy", config.groupBy(), PropertyType.SELECT,
                    "BOARD views require 'groupBy' pointing at a SELECT property", properties);
            case CALENDAR -> requireAnchor("dateProperty", config.dateProperty(), PropertyType.DATE,
                    "CALENDAR views require 'dateProperty' pointing at a DATE property", properties);
            case TABLE, GALLERY, LIST -> {
                if (config.groupBy() != null) {
                    throw invalid("'groupBy' is only supported on BOARD views");
                }
                if (config.dateProperty() != null) {
                    throw invalid("'dateProperty' is only supported on CALENDAR views");
                }
            }
        }
    }

    private void requireAnchor(String field, String propertyId, PropertyType expectedType,
                               String message, Map<UUID, CustomAppProperty> properties) {
        if (propertyId == null || propertyId.isBlank()) {
            throw invalid(message);
        }
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw invalid(field + " must be a property id");
        }
        CustomAppProperty property = properties.get(id);
        if (property == null) {
            throw invalid(field + " references a property that does not exist in this custom app");
        }
        if (property.getType() != expectedType) {
            throw invalid(field + " must reference a " + expectedType + " property (got "
                    + property.getType() + ")");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.CUSTOM_APP_VIEW_CONFIG_INVALID, message);
    }
}

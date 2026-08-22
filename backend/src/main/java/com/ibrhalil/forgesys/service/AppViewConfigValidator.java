package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppViewConfigDto;
import com.ibrhalil.forgesys.entity.AppProperty;
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
 * JSON stored in {@code t_app_views.config} (K-15 / Epic 3.0.B). Filters/sorts are
 * delegated to {@link AppQueryValidator} (shared with record search — one wire shape);
 * this class owns the view-type-specific anchors: BOARD requires a {@code groupBy}
 * (SELECT property), CALENDAR requires a {@code dateProperty} (DATE property), other
 * view types carry neither. Structured JSON only — the deliberate 3.0.B spike outcome:
 * no free-text expression language, so no expression-injection surface exists.
 */
@Component
@RequiredArgsConstructor
public class AppViewConfigValidator {

    private final AppQueryValidator appQueryValidator;
    private final ObjectMapper objectMapper;

    /**
     * @return the canonical JSON to persist, or {@code null} for an absent config.
     * A {@code null} config is treated as an EMPTY config — required anchors (BOARD
     * {@code groupBy}, CALENDAR {@code dateProperty}) are enforced even when the
     * request carries no config object at all.
     */
    public String validateAndSerialize(ViewType viewType, AppViewConfigDto config,
                                       Map<UUID, AppProperty> properties) {
        AppViewConfigDto effective = config == null
                ? new AppViewConfigDto(null, null, null, null)
                : config;
        appQueryValidator.validateFilters(effective.filters(), properties, ErrorCode.APP_VIEW_CONFIG_INVALID);
        appQueryValidator.validateSorts(effective.sorts(), properties, ErrorCode.APP_VIEW_CONFIG_INVALID);
        validateTypedAnchors(viewType, effective, properties);
        return config == null ? null : objectMapper.writeValueAsString(config);
    }

    private void validateTypedAnchors(ViewType viewType, AppViewConfigDto config,
                                      Map<UUID, AppProperty> properties) {
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
                               String message, Map<UUID, AppProperty> properties) {
        if (propertyId == null || propertyId.isBlank()) {
            throw invalid(message);
        }
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw invalid(field + " must be a property id");
        }
        AppProperty property = properties.get(id);
        if (property == null) {
            throw invalid(field + " references a property that does not exist in this app");
        }
        if (property.getType() != expectedType) {
            throw invalid(field + " must reference a " + expectedType + " property (got "
                    + property.getType() + ")");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.APP_VIEW_CONFIG_INVALID, message);
    }
}

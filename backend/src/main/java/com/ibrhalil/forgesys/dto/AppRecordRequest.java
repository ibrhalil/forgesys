package com.ibrhalil.forgesys.dto;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Create/update the values of a custom app record (K-15 / Epic 3.0.B). Keys are
 * property ids of the owning app; values are raw JSON validated against the property
 * type. On create every {@code required} property must carry a value. On update
 * (PATCH semantics) only provided keys are touched: a JSON {@code null} clears the
 * value (rejected for required properties), an absent key keeps it.
 */
public record AppRecordRequest(
        Map<String, JsonNode> values
) {
}

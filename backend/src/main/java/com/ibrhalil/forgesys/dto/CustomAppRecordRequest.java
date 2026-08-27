package com.ibrhalil.forgesys.dto;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Values of a custom app record (K-15): keys are property ids, values raw JSON
 * validated against the property type. PATCH semantics on update — JSON
 * {@code null} clears (rejected for required), absent keys keep.
 */
public record CustomAppRecordRequest(
        Map<String, JsonNode> values
) {
}

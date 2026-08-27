package com.ibrhalil.forgesys.dto;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A record (row) of a custom app with its cell values (K-15): map of
 * property id → raw JSON value. Absent key = empty cell.
 */
public record CustomAppRecordResponse(
        UUID id,
        UUID customAppId,
        Map<String, JsonNode> values,
        OffsetDateTime createdDate,
        OffsetDateTime updatedAt,
        String createdBy) {
}

package com.ibrhalil.forgesys.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Type-dependent config of a custom app property (K-15): SELECT carries
 * {@code options}, RELATION carries {@code targetCustomAppId}, others carry none.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomAppPropertyConfigDto(
        @Size(max = 100, message = "At most 100 select options")
        List<String> options,

        @Size(max = 36, message = "targetCustomAppId must be a UUID string")
        String targetCustomAppId) {
}

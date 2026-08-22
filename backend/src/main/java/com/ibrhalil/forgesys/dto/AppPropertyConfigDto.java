package com.ibrhalil.forgesys.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Type-dependent config of a custom app property (K-15 / Epic 3.0.B): SELECT carries
 * {@code options} (required, non-empty), RELATION carries {@code targetAppId}
 * (required), all other types carry no config. Validated per type in
 * {@code AppBuilderService}; stored as canonical JSON in {@code t_app_properties.config}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppPropertyConfigDto(
        @Size(max = 100, message = "At most 100 select options")
        List<String> options,

        @Size(max = 36, message = "targetAppId must be a UUID string")
        String targetAppId) {
}

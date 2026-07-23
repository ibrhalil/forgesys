package com.ibrhalil.systemforge.dto;

import java.util.UUID;

/**
 * Lightweight group reference (id + name) used inside user responses.
 */
public record GroupSummary(
        UUID id,
        String name
) {
}

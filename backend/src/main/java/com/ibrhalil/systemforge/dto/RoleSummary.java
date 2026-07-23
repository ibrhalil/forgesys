package com.ibrhalil.systemforge.dto;

import java.util.UUID;

/**
 * Lightweight role reference (id + name) used inside user/group responses to avoid
 * nesting the full permission graph repeatedly.
 */
public record RoleSummary(
        UUID id,
        String name
) {
}

package com.ibrhalil.systemforge.dto;

import java.util.List;
import java.util.UUID;

/**
 * Current-user snapshot for {@code GET /api/v1/auth/me}. Built from JWT claims
 * (no DB hit).
 */
public record MeResponse(
        UUID userId,
        String email,
        String tenant,
        List<String> authorities
) {
}

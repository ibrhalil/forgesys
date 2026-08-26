package com.ibrhalil.forgesys.dto;

import java.util.UUID;

/**
 * K-50 F6: banner data for impersonated sessions — the acting platform identity,
 * exposed by {@code GET /api/v1/users/me} when the token carries {@code imp=true}.
 * Null for real logins.
 */
public record ImpersonationInfo(
        UUID actorId,
        String actorEmail
) {
}

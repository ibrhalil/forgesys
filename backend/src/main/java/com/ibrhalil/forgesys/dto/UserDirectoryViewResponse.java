package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flat list-item projection for {@code GET /users} + {@code POST /users/search}
 * (K-49): association lists become counts; the detail endpoint still returns
 * the full role/group sets.
 */
public record UserDirectoryViewResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName,
        boolean enabled,
        OffsetDateTime lockedUntil,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdDate,
        long roleCount,
        long groupCount) {
}

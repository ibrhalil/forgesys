package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flat list-item projection of the user directory ({@code UserDirectoryView} subselect).
 * Replaces the full {@link UserResponse} on {@code GET /users} and
 * {@code POST /users/search}: association lists become counts (the detail endpoint
 * still returns the full role/group sets) — matching the UI's narrow-list convention.
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

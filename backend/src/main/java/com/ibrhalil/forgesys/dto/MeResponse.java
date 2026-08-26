package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The single self view ({@code GET /api/v1/users/me}, K-37): DB profile + the
 * authorities embedded in the access token; backs the SPA bootstrap and the
 * profile page.
 */
public record MeResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
        OffsetDateTime lockedUntil,
        String firstName,
        String lastName,
        String phoneNumber,
        String address,
        String city,
        String country,
        String zipCode,
        List<RoleSummary> roles,
        List<GroupSummary> groups,
        List<String> authorities
) {
}

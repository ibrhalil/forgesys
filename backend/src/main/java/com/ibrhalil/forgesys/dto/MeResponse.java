package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The single self endpoint ({@code GET /api/v1/users/me}): the full self view (DB
 * profile) plus the caller's authorities embedded in the access token (K-37 — the
 * former claims-only {@code GET /api/v1/auth/me} was removed; this endpoint serves
 * both the SPA bootstrap and the profile page).
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

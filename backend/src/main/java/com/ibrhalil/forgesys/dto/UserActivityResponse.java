package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;

/**
 * Temporal account-activity summary: entity stamps, last login, and the most
 * recent failed-login date (K-19; reason/IP stay on the login-history page).
 */
public record UserActivityResponse(
        OffsetDateTime createdDate,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        OffsetDateTime lastLoginAt,
        OffsetDateTime lastFailedLoginAt) {
}

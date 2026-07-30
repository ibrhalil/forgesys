package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;

/**
 * Temporal account-activity summary for the user detail view: creation/update
 * stamps from the user entity, last login from the account row, and the most
 * recent failed login attempt from the append-only login history (K-19). Date-only
 * for the failed login by decision — reason/IP stay in the login-history page.
 */
public record UserActivityResponse(
        OffsetDateTime createdDate,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        OffsetDateTime lastLoginAt,
        OffsetDateTime lastFailedLoginAt) {
}

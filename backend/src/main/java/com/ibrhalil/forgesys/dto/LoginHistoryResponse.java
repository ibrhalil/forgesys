package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant login-history entry exposed by {@code GET /api/v1/login-history}. Mirrors the
 * {@code t_login_history} record (K-19 layer 2). Both successful and failed
 * authentications are recorded; a failed attempt carries the stable {@code ErrorCode}
 * wire value as its {@code reason}.
 *
 * @param id        login-history id
 * @param userId    the authenticating user's id, or null for an unknown email
 * @param username  the attempted email
 * @param success   whether the authentication succeeded
 * @param reason    stable failure reason (e.g. {@code auth_bad_credentials}), null on success
 * @param ipAddress resolved client IP
 * @param userAgent User-Agent header (truncated)
 * @param createdAt when the attempt was recorded
 */
public record LoginHistoryResponse(
        UUID id,
        UUID userId,
        String username,
        boolean success,
        String reason,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt
) {
}

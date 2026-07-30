package com.ibrhalil.forgesys.security.refresh;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadata captured when a refresh token is issued (K-34). Stored alongside the
 * token hash so {@link RefreshTokenStore#rotate(String)} can re-resolve the user
 * (fresh authorities + account checks) without an opaque-token round-trip to the DB.
 *
 * <p><strong>Never</strong> contains the raw token string — only its hash is persisted
 * (RISK-30 hash-at-rest philosophy).
 *
 * @param userId    owner of the session
 * @param email     login identifier (email-based auth)
 * @param tenant    tenant schema the session is bound to (cross-tenant replay rejected)
 * @param issuedAt  creation instant (informational)
 */
public record RefreshSession(UUID userId, String email, String tenant, OffsetDateTime issuedAt) {
}

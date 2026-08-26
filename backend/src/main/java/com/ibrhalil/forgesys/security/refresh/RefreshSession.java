package com.ibrhalil.forgesys.security.refresh;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Session metadata stored beside the token hash so rotate can re-resolve the user
 * (fresh authorities + account checks) without a DB round-trip. Never contains the
 * raw token (RISK-30 hash-at-rest).
 */
public record RefreshSession(UUID userId, String email, String tenant, OffsetDateTime issuedAt) {
}

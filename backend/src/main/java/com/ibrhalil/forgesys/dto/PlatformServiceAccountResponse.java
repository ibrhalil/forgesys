package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** K-50 service-account view — the raw key and its hash are NEVER exposed. */
public record PlatformServiceAccountResponse(
        UUID id,
        UUID accountId,
        String name,
        List<String> scopes,
        String keyPrefix,
        OffsetDateTime expiresAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        boolean enabled,
        OffsetDateTime createdAt
) {
}

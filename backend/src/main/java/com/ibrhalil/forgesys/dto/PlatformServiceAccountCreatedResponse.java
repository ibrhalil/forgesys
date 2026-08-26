package com.ibrhalil.forgesys.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** K-50 creation response — carries the raw {@code <prefix>_<secret>} EXACTLY ONCE. */
public record PlatformServiceAccountCreatedResponse(
        UUID id,
        UUID accountId,
        String name,
        List<String> scopes,
        String keyPrefix,
        OffsetDateTime expiresAt,
        String rawKey
) {
}

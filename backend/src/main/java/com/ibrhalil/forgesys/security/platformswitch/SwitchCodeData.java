package com.ibrhalil.forgesys.security.platformswitch;

import java.util.UUID;

/**
 * K-50 F6: payload of a one-time switch code ({@code switch:code:<sha256>}) —
 * everything the tenant-side exchange needs to validate the target and mint
 * the impersonation JWT. Only the sha256 digest of the raw code is stored.
 */
public record SwitchCodeData(
        UUID companyId,
        String schemaName,
        UUID targetUserId,
        UUID actorId,
        String actorType,
        String reason
) {
}

package com.ibrhalil.forgesys.dto;

import java.util.UUID;

/**
 * K-50 F6: impersonation session credentials — token also lands in the tenant
 * {@code sf_access_token} cookie; there is NO refresh token (short-lived by design).
 */
public record PlatformSwitchExchangeResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email
) {
}

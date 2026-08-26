package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

/** K-50 platform login/refresh response — mirrors {@link LoginResponse} + displayName. */
public record PlatformLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String displayName,
        List<String> authorities
) {
}

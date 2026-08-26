package com.ibrhalil.forgesys.dto;

import java.util.List;
import java.util.UUID;

/**
 * Login/refresh response. Tokens are ALSO set as httpOnly cookies
 * ({@code sf_access_token} / {@code sf_refresh_token}) — body copies serve
 * non-browser clients; {@code refreshToken} is null on the /me shape.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        List<String> authorities
) {
}

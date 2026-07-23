package com.ibrhalil.systemforge.dto;

import java.util.List;
import java.util.UUID;

/**
 * Successful login response. The access token is also delivered via an httpOnly
 * cookie ({@code sf_access_token}); the body copy lets non-browser clients use it.
 *
 * @param accessToken RS256 JWT (also set as a cookie)
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   access-token lifetime in seconds
 * @param userId      authenticated user id
 * @param email       authenticated user email
 * @param authorities effective permissions ({module}:{resource}:{action})
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        List<String> authorities
) {
}

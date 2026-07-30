package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh-token request body (K-34). The browser path sends the refresh token in the
 * {@code sf_refresh_token} cookie; API clients use this body. The controller falls back
 * to the cookie when the body is absent, so the field is optional at the binding layer
 * (validated only when a body is present).
 *
 * @param refreshToken opaque refresh token issued at login or the previous refresh
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}

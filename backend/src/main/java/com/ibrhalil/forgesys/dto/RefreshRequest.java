package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh-token body for API clients; the browser path uses the
 * {@code sf_refresh_token} cookie (controller falls back when the body is absent,
 * so the field is validated only when a body is present).
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}

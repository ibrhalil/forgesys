package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/auth/reset-password}. The token comes from the
 * mailed link (single-use, digest-stored); the new password replaces the old one and
 * every outstanding session of the user dies with it.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        String token,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String newPassword
) {
}

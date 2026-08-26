package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/reset-password}: consumes the mailed single-use
 * token; every outstanding session of the user dies with the new password.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        String token,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String newPassword
) {
}

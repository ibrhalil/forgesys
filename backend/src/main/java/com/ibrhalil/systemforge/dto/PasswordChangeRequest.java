package com.ibrhalil.systemforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service password change ({@code PUT /api/v1/users/me/password}). The current
 * password is verified against the stored hash before the new password is applied.
 */
public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String newPassword
) {
}

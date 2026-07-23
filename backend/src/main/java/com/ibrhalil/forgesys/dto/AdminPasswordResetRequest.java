package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin-issued password reset ({@code PATCH /api/v1/users/{id}/password}). No current
 * password is required — the caller already holds {@code iam:user:write}.
 */
public record AdminPasswordResetRequest(
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be 8-255 characters")
        String newPassword
) {
}

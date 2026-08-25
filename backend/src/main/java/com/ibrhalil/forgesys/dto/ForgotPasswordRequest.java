package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/forgot-password}. The response is
 * intentionally uniform — an unknown address is indistinguishable from a delivered
 * mail (no account enumeration).
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email
) {
}

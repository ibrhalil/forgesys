package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/forgot-password}; the response is uniform — unknown
 * address is indistinguishable from a delivered mail (no enumeration).
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email
) {
}

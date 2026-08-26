package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/company/verify} (K-21); token issued by
 * {@code register} and mailed to the admin email.
 */
public record CompanyVerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}

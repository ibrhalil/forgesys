package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /api/v1/auth/verify-email}. The token was issued at user
 * creation (or resend) and delivered by mail; the tenant is resolved by {@code
 * TenantFilter} from the subdomain-anchored link host, not carried in the body.
 */
public record EmailVerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}

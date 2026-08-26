package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/verify-email}; the tenant is resolved by
 * {@code TenantFilter} from the subdomain-anchored link host, not the body.
 */
public record EmailVerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}

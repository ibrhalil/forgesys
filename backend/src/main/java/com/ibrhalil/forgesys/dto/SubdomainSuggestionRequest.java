package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/auth/company/suggest-subdomain}. Generates slug
 * candidates from an organization name so the user can pick one during signup (the
 * returned slugs are guaranteed unique among active + provisioning tenants).
 */
public record SubdomainSuggestionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name
) {
}

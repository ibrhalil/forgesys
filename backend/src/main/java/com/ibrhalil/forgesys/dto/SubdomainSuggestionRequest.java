package com.ibrhalil.forgesys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/company/suggest-subdomain}: slug candidates from an
 * organization name (returned slugs are unique among active + provisioning tenants).
 */
public record SubdomainSuggestionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name
) {
}

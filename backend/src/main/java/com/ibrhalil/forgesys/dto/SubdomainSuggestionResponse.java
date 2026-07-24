package com.ibrhalil.forgesys.dto;

import java.util.List;

/**
 * Response of {@code POST /api/v1/auth/company/suggest-subdomain}. Up to three slug
 * candidates derived from the requested name, all validated against the subdomain
 * pattern and confirmed available (no active/provisioning tenant owns them).
 */
public record SubdomainSuggestionResponse(
        List<String> suggestions
) {
}

package com.ibrhalil.forgesys.dto;

import java.util.List;

/**
 * Response of {@code POST /auth/company/suggest-subdomain}: up to three slug
 * candidates, pattern-validated and confirmed available.
 */
public record SubdomainSuggestionResponse(
        List<String> suggestions
) {
}

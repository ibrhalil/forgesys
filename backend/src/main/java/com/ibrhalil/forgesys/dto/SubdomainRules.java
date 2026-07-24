package com.ibrhalil.forgesys.dto;

import java.util.regex.Pattern;

/**
 * Shared subdomain validation rules (K-21). Single source for the subdomain pattern
 * used by both {@link CompanyRegisterRequest} (as a {@code @Pattern}) and
 * {@code SubdomainSuggestionService} (compiled), so the two cannot drift apart.
 */
public final class SubdomainRules {

    /** 1-100 chars: lowercase alphanumeric and hyphens, not starting/ending with a hyphen. */
    public static final String REGEX = "^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$";

    public static final Pattern PATTERN = Pattern.compile(REGEX);

    public static final int MAX_LENGTH = 100;

    private SubdomainRules() {
    }
}

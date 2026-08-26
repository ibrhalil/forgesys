package com.ibrhalil.forgesys.dto;

import java.util.regex.Pattern;

/**
 * Shared subdomain validation rules (K-21) — single source for
 * {@link CompanyRegisterRequest} and {@code SubdomainSuggestionService} so the
 * two cannot drift.
 */
public final class SubdomainRules {

    /** 1-100 chars: lowercase alphanumeric and hyphens, not starting/ending with a hyphen. */
    public static final String REGEX = "^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$";

    public static final Pattern PATTERN = Pattern.compile(REGEX);

    public static final int MAX_LENGTH = 100;

    private SubdomainRules() {
    }
}

package com.ibrhalil.forgesys.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit configuration (Faz 3), bound under {@code forgesys.security.rate-limit}.
 *
 * <ul>
 *   <li>{@code enabled} — master switch (default {@code true}). Disable to bypass the
 *       limiter entirely (e.g. tests that hammer the auth endpoints).</li>
 *   <li>{@code capacity} — token-bucket burst size (default {@code 20}).</li>
 *   <li>{@code refill-tokens} / {@code refill-period-seconds} — steady refill rate
 *       (defaults {@code 20} tokens per {@code 60}s, i.e. 20 requests/minute per key).</li>
 * </ul>
 *
 * <p>Applied uniformly to the auth endpoints ({@code /auth/login},
 * {@code /auth/company/verify}, {@code /auth/refresh}), keyed by scope + tenant + client IP.
 * A stricter per-endpoint profile is a K-XX follow-up; this closes the credential-stuffing
 * gap ([RISK-22] unknown-email path) at the request edge.
 */
@ConfigurationProperties(prefix = "forgesys.security.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer capacity, Integer refillTokens, Integer refillPeriodSeconds) {

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_CAPACITY = 20;
    public static final int DEFAULT_REFILL_TOKENS = 20;
    public static final int DEFAULT_REFILL_PERIOD_SECONDS = 60;

    public boolean effectiveEnabled() {
        return enabled == null || enabled;
    }

    public int effectiveCapacity() {
        return capacity != null && capacity > 0 ? capacity : DEFAULT_CAPACITY;
    }

    public int effectiveRefillTokens() {
        return refillTokens != null && refillTokens > 0 ? refillTokens : DEFAULT_REFILL_TOKENS;
    }

    public int effectiveRefillPeriodSeconds() {
        return refillPeriodSeconds != null && refillPeriodSeconds > 0 ? refillPeriodSeconds : DEFAULT_REFILL_PERIOD_SECONDS;
    }
}

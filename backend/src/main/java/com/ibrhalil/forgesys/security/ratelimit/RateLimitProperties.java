package com.ibrhalil.forgesys.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forgesys.security.rate-limit.*} (Faz 3): {@code enabled} (default true),
 * {@code capacity} burst (20), {@code refill-tokens}/{@code refill-period-seconds}
 * (20/60s = 20 req/min per scope+tenant+IP key).
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

package com.ibrhalil.forgesys.security.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-profile {@link RateLimiter} (Docker-free build, Faz 3). Mirrors the Redis Lua
 * token-bucket in a {@link ConcurrentHashMap} of {@code [tokens, lastRefillEpochSec]}
 * pairs so the default H2 test suite exercises rate-limiting without a Redis container.
 * The refill/consume math is identical to the Lua script; real Redis atomicity is
 * verified separately (the dev/prod path).
 */
@Component
@Profile("test")
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    @Override
    public synchronized RateLimitResult tryConsume(String key, int capacity, int refillTokens, int refillPeriodSeconds) {
        long now = Instant.now().getEpochSecond();
        long[] bucket = buckets.computeIfAbsent(key, k -> new long[]{capacity, now});
        long tokens = bucket[0];
        long last = bucket[1];
        long elapsed = Math.max(0, now - last);
        long refills = elapsed / refillPeriodSeconds;
        if (refills > 0) {
            tokens = Math.min(capacity, tokens + refills * refillTokens);
            last = last + refills * refillPeriodSeconds;
        }
        if (tokens >= 1) {
            bucket[0] = tokens - 1;
            bucket[1] = last;
            return new RateLimitResult(true, 0L);
        }
        long intoPeriod = elapsed - (elapsed / refillPeriodSeconds) * refillPeriodSeconds;
        long retryAfter = refillPeriodSeconds - intoPeriod;
        if (retryAfter <= 0) {
            retryAfter = refillPeriodSeconds;
        }
        bucket[0] = tokens;
        bucket[1] = last;
        return new RateLimitResult(false, retryAfter);
    }
}

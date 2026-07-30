package com.ibrhalil.forgesys.security.ratelimit;

/**
 * Token-bucket rate limiter (Faz 3). A bucket is identified by an opaque {@code key}
 * (e.g. {@code rl:login:tenant_acme:10.0.0.1}); {@link #tryConsume} atomically refills it
 * (capacity bursts, then a steady refill rate) and consumes one token, returning whether
 * the request is allowed. Implementations are profile-split: Redis (dev/prod, shared across
 * instances via a Lua script) and in-memory (test, Docker-free build).
 *
 * <p>The bucket parameters are supplied per call so a single limiter can serve endpoint
 * profiles with different limits; the key encodes the endpoint scope.
 */
public interface RateLimiter {

    /**
     * Atomically refills and consumes one token from the named bucket.
     *
     * @param key                 bucket key (opaque, caller-built)
     * @param capacity            max tokens (burst size)
     * @param refillTokens        tokens added per refill period
     * @param refillPeriodSeconds refill period in seconds
     * @return allowed/blocked decision; blocked results carry a {@code Retry-After} hint
     */
    RateLimitResult tryConsume(String key, int capacity, int refillTokens, int refillPeriodSeconds);
}

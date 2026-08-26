package com.ibrhalil.forgesys.security.ratelimit;

/**
 * Token-bucket rate limiter (Faz 3), profile-split: Redis (dev/prod, atomic Lua, shared
 * across instances) / in-memory (test). Bucket parameters are supplied per call so one
 * limiter serves endpoint profiles with different limits; the key encodes the scope.
 */
public interface RateLimiter {

    /** Atomically refills and consumes one token; blocked results carry a Retry-After hint. */
    RateLimitResult tryConsume(String key, int capacity, int refillTokens, int refillPeriodSeconds);
}

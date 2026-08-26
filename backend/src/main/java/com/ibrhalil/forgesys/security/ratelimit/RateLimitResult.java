package com.ibrhalil.forgesys.security.ratelimit;

/** Token-consumption outcome; when blocked, surface {@code retryAfterSeconds} as {@code Retry-After}. */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}

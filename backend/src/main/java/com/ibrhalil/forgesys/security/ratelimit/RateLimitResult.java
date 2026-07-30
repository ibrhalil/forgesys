package com.ibrhalil.forgesys.security.ratelimit;

/**
 * Outcome of a single rate-limit token consumption (Faz 3). When {@code allowed} is
 * {@code false} the request must be rejected with {@code 429} and {@code retryAfterSeconds}
 * surfaced as the {@code Retry-After} header so a well-behaved client backs off.
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}

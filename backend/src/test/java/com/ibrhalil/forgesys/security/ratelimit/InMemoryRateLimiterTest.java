package com.ibrhalil.forgesys.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the in-memory token-bucket math (Faz 3). The Redis Lua script mirrors this
 * exactly; real distributed atomicity is exercised by the dev/prod path.
 */
class InMemoryRateLimiterTest {

    @Test
    void allowsUpToCapacityThenBlocks() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        assertThat(limiter.tryConsume("k", 2, 2, 60).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", 2, 2, 60).allowed()).isTrue();

        RateLimitResult blocked = limiter.tryConsume("k", 2, 2, 60);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isPositive();
    }

    @Test
    void bucketsAreKeyedIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        assertThat(limiter.tryConsume("ip-a", 1, 1, 60).allowed()).isTrue();
        // a different key has its own bucket
        assertThat(limiter.tryConsume("ip-b", 1, 1, 60).allowed()).isTrue();
        // first key is now exhausted
        assertThat(limiter.tryConsume("ip-a", 1, 1, 60).allowed()).isFalse();
    }
}

package com.ibrhalil.forgesys.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deliberate Redis-outage behavior of {@link RedisRateLimiter}
 * ([RISK-36] P2 fix): a Redis connection failure must fail OPEN (auth stays up),
 * never propagate into the filter chain as a 500.
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    void redisConnectionFailureFailsOpen() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        RedisRateLimiter limiter = new RedisRateLimiter(redis);

        RateLimitResult result = limiter.tryConsume("login:tenant_acme:10.0.0.1", 20, 20, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    void emptyScriptReplyFailsOpen() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);
        RedisRateLimiter limiter = new RedisRateLimiter(redis);

        assertThat(limiter.tryConsume("login:tenant_acme:10.0.0.1", 20, 20, 60).allowed()).isTrue();
    }

    @Test
    void blockedReplyIsSurfaced() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("0", "42"));
        RedisRateLimiter limiter = new RedisRateLimiter(redis);

        RateLimitResult result = limiter.tryConsume("login:tenant_acme:10.0.0.1", 20, 20, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(42L);
    }
}

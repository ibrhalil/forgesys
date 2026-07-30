package com.ibrhalil.forgesys.security;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link TokenBlacklistService} (dev/prod, K-34). One key per revoked
 * {@code jti}: {@code bl:jti:<jti> = "1"}, TTL = remaining access-token lifetime.
 */
@Component
@Profile("!test")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final String PREFIX = "bl:jti:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}

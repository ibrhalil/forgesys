package com.ibrhalil.forgesys.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed blacklist (dev/prod): {@code bl:jti:<jti>} = "1", TTL = remaining
 * token lifetime. Fail-open by design (RISK-36) — defense-in-depth on top of signature
 * + expiry + {@code tokenInvalidBefore}, so a Redis blip must not 500 every request:
 * writes are best-effort (the token expires anyway), reads fail OPEN.
 */
@Component
@Profile("!test")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);

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
        try {
            redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
        } catch (DataAccessException e) {
            log.warn("Blacklist write failed (Redis unavailable); token expires at its TTL anyway: {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (DataAccessException e) {
            log.warn("Blacklist check failed (Redis unavailable); failing open: {}",
                    e.getMostSpecificCause().getMessage());
            return false;
        }
    }
}

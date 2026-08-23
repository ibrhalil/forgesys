package com.ibrhalil.forgesys.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link TokenBlacklistService} (dev/prod, K-34). One key per revoked
 * {@code jti}: {@code bl:jti:<jti> = "1"}, TTL = remaining access-token lifetime.
 *
 * <p>Deliberate Redis-outage behavior: the blacklist is a defense-in-depth layer on
 * top of signature + expiry + {@code tokenInvalidBefore} ([RISK-21]), so both
 * operations degrade rather than throwing — a Redis blip must not 500 every
 * authenticated request or break logout. {@link #blacklist} is best-effort (the
 * skipped entry simply expires with the token's TTL); {@link #isBlacklisted} fails
 * OPEN (the exposure window is bounded by the short access-token lifetime).
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

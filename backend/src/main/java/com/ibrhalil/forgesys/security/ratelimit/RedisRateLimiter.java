package com.ibrhalil.forgesys.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Redis-backed {@link RateLimiter} (dev/prod, Faz 3). Each bucket is a Redis hash
 * {@code rl:{key}} carrying {@code tokens} + {@code last} (refill epoch second); an atomic
 * Lua script refills (capped at {@code capacity}) and consumes one token, returning
 * {@code [allowed, retryAfterSeconds]}. The script closes the read-modify-write race two
 * concurrent requests from the same key would otherwise open (mirrors the refresh-token
 * rotation Lua in {@code RedisRefreshTokenStore}). TTL caps unbounded key growth.
 */
@Component
@Profile("!test")
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /**
     * Atomic refill + consume. ARGV: capacity, refillTokens, refillPeriodSeconds, nowEpochSec, ttlSec.
     * Returns {allowed(0/1), retryAfterSeconds}.
     */
    private static final RedisScript<List> CONSUME = new DefaultRedisScript<>("""
            local cap = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local period = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])
            local v = redis.call('HMGET', KEYS[1], 'tokens', 'last')
            local tokens = tonumber(v[1])
            local last = tonumber(v[2])
            if tokens == nil then tokens = cap; last = now end
            local elapsed = now - last
            if elapsed < 0 then elapsed = 0 end
            local refills = math.floor(elapsed / period)
            if refills > 0 then
              tokens = math.min(cap, tokens + refills * rate)
              last = last + refills * period
            end
            local allowed = 0
            local retryAfter = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              local intoPeriod = elapsed - (math.floor(elapsed / period) * period)
              retryAfter = period - intoPeriod
              if retryAfter <= 0 then retryAfter = period end
            end
            redis.call('HMSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(last))
            redis.call('EXPIRE', KEYS[1], ttl)
            return {tostring(allowed), tostring(retryAfter)}
            """, List.class);

    /** Bucket TTL — bounded so idle keys expire (slightly above the refill window). */
    private static final long TTL_SECONDS = 600;

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String key, int capacity, int refillTokens, int refillPeriodSeconds) {
        long now = Instant.now().getEpochSecond();
        List<String> res = redis.execute(CONSUME,
                List.of("rl:" + key),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillPeriodSeconds),
                String.valueOf(now),
                String.valueOf(TTL_SECONDS));
        if (res == null || res.size() < 2) {
            // Redis unavailable / unexpected reply — fail OPEN so a Redis blip never takes
            // auth down (defense-in-depth must not become a self-DoS). Logged for alerting.
            log.warn("Rate-limit Lua returned no result for key {}; failing open", key);
            return new RateLimitResult(true, 0L);
        }
        boolean allowed = "1".equals(res.get(0));
        long retryAfter = parseLong(res.get(1), refillPeriodSeconds);
        return new RateLimitResult(allowed, allowed ? 0L : retryAfter);
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

package com.ibrhalil.forgesys.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Redis token bucket (dev/prod): hash {@code rl:{key}} with {@code tokens}/{@code last};
 * an atomic Lua refill+consume closes the concurrent-request race (same pattern as the
 * refresh-rotation Lua). TTL caps idle key growth.
 */
@Component
@Profile("!test")
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /** Atomic refill + consume. ARGV: capacity, refillTokens, refillPeriodSeconds, nowEpochSec, ttlSec; returns {allowed, retryAfterSeconds}. */
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
        List<String> res;
        try {
            res = redis.execute(CONSUME,
                    List.of("rl:" + key),
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillPeriodSeconds),
                    String.valueOf(now),
                    String.valueOf(TTL_SECONDS));
        } catch (DataAccessException e) {
            // Redis down → fail OPEN by design (RISK-36): defense-in-depth must not
            // self-DoS auth; the per-account lockout (RISK-22) still applies.
            log.warn("Rate-limit Redis unavailable for key {}; failing open: {}", key, e.getMostSpecificCause().getMessage());
            return new RateLimitResult(true, 0L);
        }
        if (res == null || res.size() < 2) {
            // Unexpected reply — fail open (see above).
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

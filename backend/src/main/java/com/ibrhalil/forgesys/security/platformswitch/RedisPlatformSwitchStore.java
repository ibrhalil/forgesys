package com.ibrhalil.forgesys.security.platformswitch;

import com.ibrhalil.forgesys.security.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed switch store (dev/prod). RISK-36 posture: {@link #issue} fails
 * closed (DataAccessException → 503 — no code without Redis); {@link #claim}
 * degrades to empty (clean 401); guard ops degrade fail-open (impersonation
 * keeps working during an outage, losing only the single-active guarantee).
 */
@Component
@Profile("!test")
public class RedisPlatformSwitchStore implements PlatformSwitchStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPlatformSwitchStore.class);

    private static final String CODE_PREFIX = "switch:code:";
    private static final String ACTIVE_PREFIX = "switch:active:";

    /** Atomic single-use claim: GET + DEL in one script (no consume race). */
    private static final RedisScript<String> CLAIM = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if v == false then return nil end
            redis.call('DEL', KEYS[1])
            return v
            """, String.class);

    /** Compare-and-delete — only removes the guard when it still holds the expected value. */
    private static final RedisScript<Long> DELETE_IF_VALUE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisPlatformSwitchStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public String issue(SwitchCodeData data, Duration codeTtl) {
        String raw = PlatformSwitchStore.generateRawCode();
        String json = objectMapper.writeValueAsString(data);
        redis.opsForValue().set(codeKey(raw), json, codeTtl);
        return raw;
    }

    @Override
    public Optional<SwitchCodeData> claim(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        String json;
        try {
            json = redis.execute(CLAIM, List.of(codeKey(rawCode)));
        } catch (DataAccessException e) {
            log.warn("Switch-code claim failed (Redis unavailable); treating as unknown code: {}",
                    e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, SwitchCodeData.class));
    }

    @Override
    public boolean tryReserveActor(UUID actorId, Duration ttl) {
        try {
            Boolean reserved = redis.opsForValue().setIfAbsent(activeKey(actorId), PENDING, ttl);
            return reserved == null || reserved;
        } catch (DataAccessException e) {
            log.warn("Switch guard reservation failed (Redis unavailable); proceeding fail-open: {}",
                    e.getMostSpecificCause().getMessage());
            return true;
        }
    }

    @Override
    public void releaseReservation(UUID actorId) {
        try {
            redis.execute(DELETE_IF_VALUE, List.of(activeKey(actorId)), PENDING);
        } catch (DataAccessException e) {
            log.warn("Switch guard release failed (left to expire): {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    @Override
    public void activate(UUID actorId, String jti, Duration ttl) {
        try {
            redis.opsForValue().set(activeKey(actorId), jti, ttl);
        } catch (DataAccessException e) {
            log.warn("Switch guard activation failed (fail-open, no single-active guarantee): {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    @Override
    public boolean clearActiveIfCurrent(UUID actorId, String jti) {
        try {
            Long removed = redis.execute(DELETE_IF_VALUE, List.of(activeKey(actorId)), jti);
            return removed != null && removed > 0;
        } catch (DataAccessException e) {
            log.warn("Switch guard clear failed (left to expire): {}",
                    e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    private static String codeKey(String rawCode) {
        return CODE_PREFIX + TokenHasher.sha256Hex(rawCode);
    }

    private static String activeKey(UUID actorId) {
        return ACTIVE_PREFIX + actorId;
    }
}

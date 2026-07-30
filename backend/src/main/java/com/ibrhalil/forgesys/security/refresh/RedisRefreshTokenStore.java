package com.ibrhalil.forgesys.security.refresh;

import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link RefreshTokenStore} (dev/prod, K-34). Token records are Redis
 * hashes keyed by {@code refresh:tok:<sha256>} (value carries state/userId/email/tenant);
 * a per-user index set {@code refresh:idx:<tenant>:<userId>} backs
 * {@link #revokeAllForUser(UUID, String)}. TTL = refresh-token lifetime.
 *
 * <p>Rotation is an atomic Lua conditional: only an {@code ACTIVE} token flips to
 * {@code ROTATED} and returns its metadata; a {@code ROTATED} token reports reuse.
 * This closes the read-modify-write race two concurrent refreshes would otherwise open.
 */
@Component
@Profile("!test")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenStore.class);

    private static final String TOKEN_PREFIX = "refresh:tok:";
    private static final String INDEX_PREFIX = "refresh:idx:";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_ROTATED = "ROTATED";

    /**
     * Atomic rotate. Returns a flat list whose first element is a status
     * ({@code OK}/{@code REUSE}/{@code NIL}); {@code OK} is followed by userId/email/tenant.
     */
    private static final RedisScript<List> ROTATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return {'NIL'} end
            if redis.call('HGET', KEYS[1], 'state') ~= 'ACTIVE' then
              local r = redis.call('HMGET', KEYS[1], 'userId', 'tenant')
              return {'REUSE', r[1], r[2]}
            end
            redis.call('HSET', KEYS[1], 'state', 'ROTATED', 'rotatedTo', ARGV[1])
            local v = redis.call('HMGET', KEYS[1], 'userId', 'email', 'tenant', 'issuedAt')
            return {'OK', v[1], v[2], v[3], v[4]}
            """, List.class);

    private final StringRedisTemplate redis;
    private final long ttlSeconds;
    private final SecureRandom random = new SecureRandom();

    public RedisRefreshTokenStore(StringRedisTemplate redis, JwtCookieProperties properties) {
        this.redis = redis;
        this.ttlSeconds = Math.max(1L, properties.effectiveRefreshTokenTtlDays() * 86_400L);
    }

    @Override
    public IssuedRefresh issue(UUID userId, String email, String tenant) {
        String raw = generateToken();
        String hash = sha256Hex(raw);
        writeActive(hash, userId, email, tenant);
        indexAdd(tenant, userId, hash);
        log.debug("Issued refresh token for user {} tenant {}", userId, tenant);
        return new IssuedRefresh(raw, new RefreshSession(userId, email, tenant, OffsetDateTime.now()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public RotationResult rotate(String presented) {
        if (presented == null || presented.isBlank()) {
            return new RotationResult.Unknown();
        }
        String oldHash = sha256Hex(presented);
        String newRaw = generateToken();
        String newHash = sha256Hex(newRaw);
        List<String> res = redis.execute(ROTATE, List.of(tokenKey(oldHash)), newHash);
        if (res == null || res.isEmpty()) {
            return new RotationResult.Unknown();
        }
        String status = res.get(0);
        return switch (status) {
            case "OK" -> {
                UUID userId = parseUserId(res, 1);
                String email = string(res, 2);
                String tenant = string(res, 3);
                if (userId == null) {
                    yield new RotationResult.Unknown();
                }
                writeActive(newHash, userId, email, tenant);
                indexAdd(tenant, userId, newHash);
                log.debug("Rotated refresh token for user {} tenant {}", userId, tenant);
                yield new RotationResult.Rotated(new IssuedRefresh(newRaw,
                        new RefreshSession(userId, email, tenant, OffsetDateTime.now())));
            }
            case "REUSE" -> {
                UUID userId = parseUserId(res, 1);
                String tenant = string(res, 2);
                if (userId == null) {
                    yield new RotationResult.Unknown();
                }
                log.warn("Refresh token reuse detected for user {} tenant {}", userId, tenant);
                yield new RotationResult.ReuseDetected(userId, tenant);
            }
            default -> new RotationResult.Unknown();
        };
    }

    @Override
    public boolean revoke(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        String hash = sha256Hex(presented);
        String key = tokenKey(hash);
        Map<Object, Object> record = redis.opsForHash().entries(key);
        if (record.isEmpty()) {
            return false;
        }
        redis.delete(key);
        UUID userId = parseUserId(record.get("userId"));
        if (userId != null) {
            String tenant = string(record.get("tenant"));
            redis.opsForSet().remove(indexKey(tenant, userId), hash);
        }
        return true;
    }

    @Override
    public void revokeAllForUser(UUID userId, String tenant) {
        String idx = indexKey(tenant, userId);
        Set<String> hashes = redis.opsForSet().members(idx);
        if (hashes != null) {
            for (String h : hashes) {
                redis.delete(tokenKey(h));
            }
        }
        redis.delete(idx);
        log.debug("Revoked all refresh tokens for user {} tenant {}", userId, tenant);
    }

    // --- helpers --------------------------------------------------------

    private void writeActive(String hash, UUID userId, String email, String tenant) {
        String key = tokenKey(hash);
        redis.opsForHash().putAll(key, Map.of(
                "state", STATE_ACTIVE,
                "userId", userId.toString(),
                "email", email == null ? "" : email,
                "tenant", tenant == null ? "" : tenant,
                "issuedAt", OffsetDateTime.now().toString()));
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    private void indexAdd(String tenant, UUID userId, String hash) {
        String idx = indexKey(tenant, userId);
        redis.opsForSet().add(idx, hash);
        redis.expire(idx, Duration.ofSeconds(ttlSeconds));
    }

    private String tokenKey(String hash) {
        return TOKEN_PREFIX + hash;
    }

    private String indexKey(String tenant, UUID userId) {
        return INDEX_PREFIX + (tenant == null ? "" : tenant) + ":" + userId;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static UUID parseUserId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUserId(List<String> res, int idx) {
        if (idx >= res.size()) {
            return null;
        }
        return parseUserId(res.get(idx));
    }

    private static String string(List<String> res, int idx) {
        return idx < res.size() ? res.get(idx) : null;
    }

    private static String string(Object raw) {
        return raw == null ? null : raw.toString();
    }
}

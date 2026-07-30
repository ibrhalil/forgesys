package com.ibrhalil.forgesys.security.refresh;

import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link RefreshTokenStore} (dev/prod, K-34 + K-28). Token records are Redis
 * hashes keyed by {@code refresh:tok:<sha256>} (value carries state/userId/email/tenant
 * plus the K-28 session metadata: sessionId/ipAddress/userAgent/loginAt/lastSeen); a
 * per-user index set {@code refresh:idx:<tenant>:<userId>} backs
 * {@link #revokeAllForUser(UUID, String)} and {@link #listSessions(UUID, String)}.
 * TTL = refresh-token lifetime.
 *
 * <p>Rotation is an atomic Lua conditional: only an {@code ACTIVE} token flips to
 * {@code ROTATED} and returns its metadata; a {@code ROTATED} token reports reuse.
 * This closes the read-modify-write race two concurrent refreshes would otherwise open.
 * The stable {@code sessionId} and original device metadata are preserved across
 * rotation; only {@code lastSeen} advances.
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
     * ({@code OK}/{@code REUSE}/{@code NIL}); {@code OK} is followed by
     * userId/email/tenant/sessionId/ipAddress/userAgent/loginAt so the caller can write
     * the rotated record preserving the original device metadata.
     */
    private static final RedisScript<List> ROTATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return {'NIL'} end
            if redis.call('HGET', KEYS[1], 'state') ~= 'ACTIVE' then
              local r = redis.call('HMGET', KEYS[1], 'userId', 'tenant')
              return {'REUSE', r[1], r[2]}
            end
            redis.call('HSET', KEYS[1], 'state', 'ROTATED', 'rotatedTo', ARGV[1])
            local v = redis.call('HMGET', KEYS[1], 'userId', 'email', 'tenant', 'sessionId', 'ipAddress', 'userAgent', 'loginAt')
            return {'OK', v[1], v[2], v[3], v[4], v[5], v[6], v[7]}
            """, List.class);

    private final StringRedisTemplate redis;
    private final long ttlSeconds;
    private final SecureRandom random = new SecureRandom();

    public RedisRefreshTokenStore(StringRedisTemplate redis, JwtCookieProperties properties) {
        this.redis = redis;
        this.ttlSeconds = Math.max(1L, properties.effectiveRefreshTokenTtlDays() * 86_400L);
    }

    @Override
    public IssuedRefresh issue(UUID userId, String email, String tenant, String ipAddress, String userAgent) {
        String raw = generateToken();
        String hash = sha256Hex(raw);
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = UUID.randomUUID();
        writeActive(hash, userId, email, tenant, sessionId, ipAddress, userAgent, now, now);
        indexAdd(tenant, userId, hash);
        log.debug("Issued refresh token for user {} tenant {}", userId, tenant);
        return new IssuedRefresh(raw, new RefreshSession(userId, email, tenant, now));
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
                UUID sessionId = parseUserId(res, 4);
                String ipAddress = string(res, 5);
                String userAgent = string(res, 6);
                OffsetDateTime loginAt = parseOffsetDateTime(res, 7);
                if (userId == null) {
                    yield new RotationResult.Unknown();
                }
                OffsetDateTime now = OffsetDateTime.now();
                writeActive(newHash, userId, email, tenant, sessionId, ipAddress, userAgent, loginAt, now);
                indexAdd(tenant, userId, newHash);
                log.debug("Rotated refresh token for user {} tenant {}", userId, tenant);
                yield new RotationResult.Rotated(new IssuedRefresh(newRaw,
                        new RefreshSession(userId, email, tenant, now)));
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
        // Follow the rotation chain: a ROTATED record's `rotatedTo` successor is the
        // live token of the SAME session (rotation preserves sessionId). Revoking an
        // already-rotated token (logout racing a silent refresh) must kill the successor
        // too, or the session survives logout and keeps showing ACTIVE to admins.
        boolean revokedAny = false;
        Set<String> visited = new HashSet<>();
        String currentHash = sha256Hex(presented);
        while (currentHash != null && visited.add(currentHash)) {
            String key = tokenKey(currentHash);
            Map<Object, Object> record = redis.opsForHash().entries(key);
            if (record.isEmpty()) {
                break;
            }
            redis.delete(key);
            revokedAny = true;
            UUID userId = parseUserId(record.get("userId"));
            if (userId != null) {
                String tenant = string(record.get("tenant"));
                redis.opsForSet().remove(indexKey(tenant, userId), currentHash);
            }
            // null for ACTIVE records (no successor) → the chain ends.
            currentHash = string(record.get("rotatedTo"));
        }
        return revokedAny;
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

    @Override
    public List<ActiveSession> listSessions(UUID userId, String tenant) {
        Set<String> hashes = redis.opsForSet().members(indexKey(tenant, userId));
        if (hashes == null) {
            return List.of();
        }
        List<ActiveSession> sessions = new ArrayList<>();
        for (String h : hashes) {
            Map<Object, Object> record = redis.opsForHash().entries(tokenKey(h));
            if (record.isEmpty() || !STATE_ACTIVE.equals(record.get("state"))) {
                continue;
            }
            ActiveSession session = toActiveSession(record);
            if (session != null) {
                sessions.add(session);
            }
        }
        // Newest activity first.
        sessions.sort((a, b) -> {
            int c = b.lastSeen().compareTo(a.lastSeen());
            return c != 0 ? c : b.loginAt().compareTo(a.loginAt());
        });
        return sessions;
    }

    @Override
    public List<ActiveSession> listAllSessions(String tenant) {
        // Enumerate the tenant's per-user index keys (refresh:idx:<tenant>:<userId>) via
        // SCAN, resolve the userId from each key, and reuse listSessions per user. No
        // write-path change; bounded by the number of active refresh tokens. Tenant
        // schema names contain no ':' so lastIndexOf cleanly splits the userId.
        String match = INDEX_PREFIX + (tenant == null ? "" : tenant) + ":*";
        Set<String> keys = new LinkedHashSet<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(match).count(200).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        List<ActiveSession> sessions = new ArrayList<>();
        for (String key : keys) {
            int lastColon = key.lastIndexOf(':');
            if (lastColon < 0 || lastColon == key.length() - 1) {
                continue;
            }
            UUID userId = parseUserId(key.substring(lastColon + 1));
            if (userId != null) {
                sessions.addAll(listSessions(userId, tenant));
            }
        }
        sessions.sort((a, b) -> {
            int c = b.lastSeen().compareTo(a.lastSeen());
            return c != 0 ? c : b.loginAt().compareTo(a.loginAt());
        });
        return sessions;
    }

    @Override
    public boolean revokeSession(UUID userId, String tenant, UUID sessionId) {
        String idx = indexKey(tenant, userId);
        Set<String> hashes = redis.opsForSet().members(idx);
        if (hashes == null) {
            return false;
        }
        for (String h : hashes) {
            String key = tokenKey(h);
            Map<Object, Object> record = redis.opsForHash().entries(key);
            if (record.isEmpty() || !STATE_ACTIVE.equals(record.get("state"))) {
                continue;
            }
            if (sessionId.equals(parseUserId(record.get("sessionId")))) {
                redis.delete(key);
                redis.opsForSet().remove(idx, h);
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<ActiveSession> activeSessionFor(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256Hex(presentedToken);
        Map<Object, Object> record = redis.opsForHash().entries(tokenKey(hash));
        if (record.isEmpty() || !STATE_ACTIVE.equals(record.get("state"))) {
            return Optional.empty();
        }
        return Optional.ofNullable(toActiveSession(record));
    }

    // --- helpers --------------------------------------------------------

    private void writeActive(String hash, UUID userId, String email, String tenant,
                             UUID sessionId, String ipAddress, String userAgent,
                             OffsetDateTime loginAt, OffsetDateTime lastSeen) {
        String key = tokenKey(hash);
        redis.opsForHash().putAll(key, Map.of(
                "state", STATE_ACTIVE,
                "userId", userId.toString(),
                "email", email == null ? "" : email,
                "tenant", tenant == null ? "" : tenant,
                "sessionId", sessionId.toString(),
                "ipAddress", ipAddress == null ? "" : ipAddress,
                "userAgent", userAgent == null ? "" : userAgent,
                "loginAt", (loginAt == null ? OffsetDateTime.now() : loginAt).toString(),
                "lastSeen", (lastSeen == null ? OffsetDateTime.now() : lastSeen).toString()));
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    private ActiveSession toActiveSession(Map<Object, Object> record) {
        UUID userId = parseUserId(record.get("userId"));
        UUID sessionId = parseUserId(record.get("sessionId"));
        if (userId == null || sessionId == null) {
            return null;
        }
        return new ActiveSession(
                sessionId,
                userId,
                string(record.get("email")),
                string(record.get("tenant")),
                string(record.get("ipAddress")),
                string(record.get("userAgent")),
                parseOffsetDateTime(record.get("loginAt")),
                parseOffsetDateTime(record.get("lastSeen")));
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

    private static OffsetDateTime parseOffsetDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetDateTime(List<String> res, int idx) {
        if (idx >= res.size()) {
            return null;
        }
        return parseOffsetDateTime(res.get(idx));
    }

    private static String string(List<String> res, int idx) {
        return idx < res.size() ? res.get(idx) : null;
    }

    private static String string(Object raw) {
        String value = raw == null ? null : raw.toString();
        return (value == null || value.isEmpty()) ? null : value;
    }
}

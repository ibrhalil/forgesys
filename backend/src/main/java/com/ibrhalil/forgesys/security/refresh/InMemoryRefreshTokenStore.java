package com.ibrhalil.forgesys.security.refresh;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-profile {@link RefreshTokenStore} (Docker-free build, K-34 + K-28). Mirrors the
 * Redis state machine (ACTIVE→ROTATED, reuse detection, per-user index) in plain
 * concurrent maps so the default H2 test suite exercises refresh rotation/reuse and
 * session listing/revoke without a Redis container.
 *
 * <p>TTL/expiry semantics are intentionally not enforced here — those are verified
 * against real Redis by the gated {@code RedisRefreshTokenIT}
 * ({@code -Dforgesys.redis.it=true}).
 */
@Component
@Profile("test")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private record Entry(
            String state, UUID userId, String email, String tenant, String rotatedTo,
            UUID sessionId, String ipAddress, String userAgent,
            OffsetDateTime loginAt, OffsetDateTime lastSeen) {
    }

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> index = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public synchronized IssuedRefresh issue(UUID userId, String email, String tenant, String ipAddress, String userAgent) {
        String raw = generateToken();
        String hash = sha256Hex(raw);
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = UUID.randomUUID();
        tokens.put(hash, new Entry("ACTIVE", userId, email, tenant, null, sessionId, ipAddress, userAgent, now, now));
        index(tenant, userId).add(hash);
        return new IssuedRefresh(raw, new RefreshSession(userId, email, tenant, now));
    }

    @Override
    public synchronized RotationResult rotate(String presented) {
        if (presented == null || presented.isBlank()) {
            return new RotationResult.Unknown();
        }
        String oldHash = sha256Hex(presented);
        Entry entry = tokens.get(oldHash);
        if (entry == null) {
            return new RotationResult.Unknown();
        }
        if (!"ACTIVE".equals(entry.state)) {
            return new RotationResult.ReuseDetected(entry.userId, entry.tenant);
        }
        String newRaw = generateToken();
        String newHash = sha256Hex(newRaw);
        OffsetDateTime now = OffsetDateTime.now();
        tokens.put(oldHash, new Entry("ROTATED", entry.userId, entry.email, entry.tenant, newHash,
                entry.sessionId, entry.ipAddress, entry.userAgent, entry.loginAt, entry.lastSeen));
        // Preserved sessionId + original device metadata; lastSeen advances.
        tokens.put(newHash, new Entry("ACTIVE", entry.userId, entry.email, entry.tenant, null,
                entry.sessionId, entry.ipAddress, entry.userAgent, entry.loginAt, now));
        index(entry.tenant, entry.userId).add(newHash);
        return new RotationResult.Rotated(new IssuedRefresh(newRaw,
                new RefreshSession(entry.userId, entry.email, entry.tenant, now)));
    }

    @Override
    public synchronized boolean revoke(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        String hash = sha256Hex(presented);
        Entry entry = tokens.remove(hash);
        if (entry == null) {
            return false;
        }
        Set<String> set = index.get(indexKey(entry.tenant, entry.userId));
        if (set != null) {
            set.remove(hash);
        }
        return true;
    }

    @Override
    public synchronized void revokeAllForUser(UUID userId, String tenant) {
        Set<String> set = index.remove(indexKey(tenant, userId));
        if (set != null) {
            for (String h : set) {
                tokens.remove(h);
            }
        }
    }

    @Override
    public synchronized List<ActiveSession> listSessions(UUID userId, String tenant) {
        Set<String> set = index.get(indexKey(tenant, userId));
        if (set == null) {
            return List.of();
        }
        List<ActiveSession> sessions = new ArrayList<>();
        for (String h : set) {
            Entry entry = tokens.get(h);
            if (entry == null || !"ACTIVE".equals(entry.state)) {
                continue;
            }
            sessions.add(new ActiveSession(entry.sessionId, entry.userId, entry.email, entry.tenant,
                    entry.ipAddress, entry.userAgent, entry.loginAt, entry.lastSeen));
        }
        sessions.sort(Comparator.comparing(ActiveSession::lastSeen, Comparator.nullsLast(Comparator.reverseOrder())));
        return sessions;
    }

    @Override
    public synchronized boolean revokeSession(UUID userId, String tenant, UUID sessionId) {
        Set<String> set = index.get(indexKey(tenant, userId));
        if (set == null) {
            return false;
        }
        for (String h : set) {
            Entry entry = tokens.get(h);
            if (entry == null || !"ACTIVE".equals(entry.state)) {
                continue;
            }
            if (sessionId.equals(entry.sessionId)) {
                tokens.remove(h);
                set.remove(h);
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized Optional<ActiveSession> activeSessionFor(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tokens.get(sha256Hex(presentedToken));
        if (entry == null || !"ACTIVE".equals(entry.state)) {
            return Optional.empty();
        }
        return Optional.of(new ActiveSession(entry.sessionId, entry.userId, entry.email, entry.tenant,
                entry.ipAddress, entry.userAgent, entry.loginAt, entry.lastSeen));
    }

    private Set<String> index(String tenant, UUID userId) {
        return index.computeIfAbsent(indexKey(tenant, userId), k -> ConcurrentHashMap.newKeySet());
    }

    private String indexKey(String tenant, UUID userId) {
        return (tenant == null ? "" : tenant) + ":" + userId;
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
}

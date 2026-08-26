package com.ibrhalil.forgesys.security.platformswitch;

import com.ibrhalil.forgesys.security.TokenHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-profile store (Docker-free build); TTL semantics are intentionally not
 * enforced — verified against real Redis via the dev/prod impl (InMemoryRefreshTokenStore
 * precedent). {@link #expireAllCodes()} is the test hook simulating code TTL expiry.
 */
@Component
@Profile("test")
public class InMemoryPlatformSwitchStore implements PlatformSwitchStore {

    private final Map<String, SwitchCodeData> codes = new ConcurrentHashMap<>();
    private final Map<UUID, String> active = new ConcurrentHashMap<>();

    @Override
    public synchronized String issue(SwitchCodeData data, Duration codeTtl) {
        String raw = PlatformSwitchStore.generateRawCode();
        codes.put(TokenHasher.sha256Hex(raw), data);
        return raw;
    }

    @Override
    public synchronized Optional<SwitchCodeData> claim(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(codes.remove(TokenHasher.sha256Hex(rawCode)));
    }

    @Override
    public synchronized boolean tryReserveActor(UUID actorId, Duration ttl) {
        return active.putIfAbsent(actorId, PENDING) == null;
    }

    @Override
    public synchronized void releaseReservation(UUID actorId) {
        active.remove(actorId, PENDING);
    }

    @Override
    public synchronized void activate(UUID actorId, String jti, Duration ttl) {
        active.put(actorId, jti);
    }

    @Override
    public synchronized boolean clearActiveIfCurrent(UUID actorId, String jti) {
        return active.remove(actorId, jti);
    }

    /** Test hook: simulates the 30s code TTL lapsing. */
    public synchronized void expireAllCodes() {
        codes.clear();
    }
}

package com.ibrhalil.forgesys.security.platformswitch;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * K-50 F6 one-time switch codes + the single-impersonation concurrency guard
 * (RefreshTokenStore pattern: Redis dev/prod, in-memory test). Key families:
 * {@code switch:code:<sha256>} (JSON payload, TTL 30s, single-use) and
 * {@code switch:active:<actorId>} ({@link #PENDING} reservation at start →
 * impersonation {@code jti} at exchange, TTL = impersonation lifetime).
 */
public interface PlatformSwitchStore {

    /** Reservation marker written by {@link #tryReserveActor}; replaced by the jti on activate. */
    String PENDING = "pending";

    /** Stores the code payload and returns the raw code (shown to the operator exactly once). */
    String issue(SwitchCodeData data, Duration codeTtl);

    /** Atomically consumes the code (single-use); empty when unknown/expired/already used. */
    Optional<SwitchCodeData> claim(String rawCode);

    /**
     * Single-active guard: reserves the actor slot unless one is already held
     * ({@code SET NX} semantics) — {@code false} means an impersonation (or a
     * not-yet-redeemed switch) is already in flight.
     */
    boolean tryReserveActor(UUID actorId, Duration ttl);

    /** Best-effort rollback of a {@link #PENDING} reservation (issue failed after reserve). */
    void releaseReservation(UUID actorId);

    /** Overwrites the reservation with the live impersonation {@code jti}. */
    void activate(UUID actorId, String jti, Duration ttl);

    /** Deletes the guard only when it still holds the given {@code jti} (compare-and-delete). */
    boolean clearActiveIfCurrent(UUID actorId, String jti);

    /** 32 random bytes, URL-safe Base64 (refresh-token generation convention). */
    static String generateRawCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.ibrhalil.forgesys.security.refresh;

import java.util.UUID;

/**
 * Sealed rotate outcome (K-34): {@link Rotated} = successor issued in the same chain;
 * {@link ReuseDetected} = compromise signal — the caller MUST revoke the user's sessions
 * and stamp {@code tokenInvalidBefore}; {@link Unknown} = absent/expired, no
 * identifiable user.
 */
public sealed interface RotationResult permits
        RotationResult.Rotated, RotationResult.ReuseDetected, RotationResult.Unknown {

    record Rotated(IssuedRefresh issued) implements RotationResult {
    }

    record ReuseDetected(UUID userId, String tenant) implements RotationResult {
    }

    record Unknown() implements RotationResult {
    }
}

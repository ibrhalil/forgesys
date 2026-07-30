package com.ibrhalil.forgesys.security.refresh;

import java.util.UUID;

/**
 * Outcome of {@link RefreshTokenStore#rotate(String)} (K-34 refresh rotation with
 * reuse detection). Sealed so the caller must handle every case exhaustively.
 *
 * <ul>
 *   <li>{@link Rotated} — the presented token was active and has been consumed; a new
 *       token in the same chain was issued and is returned.</li>
 *   <li>{@link ReuseDetected} — the presented token was already consumed (rotated).
 *       This is a reuse/compromise signal: the caller MUST revoke the user's sessions
 *       (refresh store {@code revokeAllForUser}) and stamp {@code tokenInvalidBefore}
 *       so outstanding access tokens die too.</li>
 *   <li>{@link Unknown} — the token is absent/expired/invalid. No user is identifiable.</li>
 * </ul>
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

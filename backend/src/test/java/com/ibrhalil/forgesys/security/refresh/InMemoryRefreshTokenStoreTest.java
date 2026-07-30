package com.ibrhalil.forgesys.security.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the refresh-token state machine (issue/rotate/reuse/revoke/revokeAll) on the
 * test-profile in-memory store. The Redis counterpart's atomicity is verified by the
 * gated {@code RedisRefreshTokenIT}; the state transitions are identical by design.
 */
class InMemoryRefreshTokenStoreTest {

    private InMemoryRefreshTokenStore store;
    private final UUID userId = UUID.randomUUID();
    private static final String TENANT = "tenant_acme";

    @BeforeEach
    void setUp() {
        store = new InMemoryRefreshTokenStore();
    }

    @Test
    void rotateConsumesActiveTokenAndIssuesANewOne() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "Mozilla/5.0");

        RotationResult result = store.rotate(first.token());

        assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        IssuedRefresh rotated = ((RotationResult.Rotated) result).issued();
        assertThat(rotated.token()).isNotEqualTo(first.token());
        assertThat(rotated.session().userId()).isEqualTo(userId);
        assertThat(rotated.session().tenant()).isEqualTo(TENANT);
    }

    @Test
    void presentingAnAlreadyConsumedTokenSignalsReuse() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT, null, null);
        store.rotate(first.token()); // legitimate consume

        RotationResult result = store.rotate(first.token()); // replay → reuse

        assertThat(result).isInstanceOf(RotationResult.ReuseDetected.class);
        RotationResult.ReuseDetected reuse = (RotationResult.ReuseDetected) result;
        assertThat(reuse.userId()).isEqualTo(userId);
        assertThat(reuse.tenant()).isEqualTo(TENANT);
    }

    @Test
    void unknownOrBlankTokenIsUnknown() {
        assertThat(store.rotate("never-issued")).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.rotate(null)).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.rotate("  ")).isInstanceOf(RotationResult.Unknown.class);
    }

    @Test
    void revokeRemovesASingleToken() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT, null, null);

        assertThat(store.revoke(first.token())).isTrue();
        assertThat(store.rotate(first.token())).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.revoke(first.token())).isFalse();
    }

    @Test
    void revokeAllForUserKillsEveryActiveToken() {
        IssuedRefresh a = store.issue(userId, "u@acme.com", TENANT, null, null);
        IssuedRefresh b = store.issue(userId, "u@acme.com", TENANT, null, null);

        store.revokeAllForUser(userId, TENANT);

        assertThat(store.rotate(a.token())).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.rotate(b.token())).isInstanceOf(RotationResult.Unknown.class);
    }

    /* ── K-28 session management ── */

    @Test
    void listSessionsReturnsActiveSessionsWithCapturedDeviceMetadata() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "Mozilla/5.0 Mac");
        IssuedRefresh second = store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "curl/8.0");

        java.util.List<ActiveSession> sessions = store.listSessions(userId, TENANT);

        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(ActiveSession::ipAddress)
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
        // each session has a distinct stable id, resolvable from its token
        assertThat(store.activeSessionFor(first.token()).orElseThrow().sessionId())
                .isNotEqualTo(store.activeSessionFor(second.token()).orElseThrow().sessionId());
    }

    @Test
    void rotationPreservesSessionIdAndOriginalMetadataWhileAdvancingLastSeen() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "Mozilla/5.0");
        UUID originalSessionId = store.activeSessionFor(first.token()).orElseThrow().sessionId();
        OffsetDateTime originalLoginAt = store.activeSessionFor(first.token()).orElseThrow().loginAt();

        RotationResult result = store.rotate(first.token());
        IssuedRefresh rotated = ((RotationResult.Rotated) result).issued();

        // The old token is gone; the rotated token carries the SAME session id + device.
        assertThat(store.activeSessionFor(first.token())).isEmpty();
        ActiveSession after = store.activeSessionFor(rotated.token()).orElseThrow();
        assertThat(after.sessionId()).isEqualTo(originalSessionId);
        assertThat(after.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(after.loginAt()).isEqualTo(originalLoginAt);
        assertThat(store.listSessions(userId, TENANT)).hasSize(1);
    }

    @Test
    void revokeSessionEndsASingleDeviceWithoutTouchingOthers() {
        IssuedRefresh a = store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "UA-1");
        IssuedRefresh b = store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "UA-2");
        UUID aId = store.activeSessionFor(a.token()).orElseThrow().sessionId();

        boolean ended = store.revokeSession(userId, TENANT, aId);

        assertThat(ended).isTrue();
        assertThat(store.rotate(a.token())).isInstanceOf(RotationResult.Unknown.class);
        // b keeps working
        assertThat(store.rotate(b.token())).isInstanceOf(RotationResult.Rotated.class);
        assertThat(store.listSessions(userId, TENANT)).hasSize(1);
    }

    @Test
    void revokeSessionReturnsFalseForUnknownSessionId() {
        store.issue(userId, "u@acme.com", TENANT, null, null);
        assertThat(store.revokeSession(userId, TENANT, java.util.UUID.randomUUID())).isFalse();
    }
}

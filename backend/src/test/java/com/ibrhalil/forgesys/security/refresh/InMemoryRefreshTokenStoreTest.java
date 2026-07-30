package com.ibrhalil.forgesys.security.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT);

        RotationResult result = store.rotate(first.token());

        assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        IssuedRefresh rotated = ((RotationResult.Rotated) result).issued();
        assertThat(rotated.token()).isNotEqualTo(first.token());
        assertThat(rotated.session().userId()).isEqualTo(userId);
        assertThat(rotated.session().tenant()).isEqualTo(TENANT);
    }

    @Test
    void presentingAnAlreadyConsumedTokenSignalsReuse() {
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT);
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
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT);

        assertThat(store.revoke(first.token())).isTrue();
        assertThat(store.rotate(first.token())).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.revoke(first.token())).isFalse();
    }

    @Test
    void revokeAllForUserKillsEveryActiveToken() {
        IssuedRefresh a = store.issue(userId, "u@acme.com", TENANT);
        IssuedRefresh b = store.issue(userId, "u@acme.com", TENANT);

        store.revokeAllForUser(userId, TENANT);

        assertThat(store.rotate(a.token())).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.rotate(b.token())).isInstanceOf(RotationResult.Unknown.class);
    }
}

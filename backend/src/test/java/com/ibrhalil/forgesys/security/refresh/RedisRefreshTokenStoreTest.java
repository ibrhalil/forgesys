package com.ibrhalil.forgesys.security.refresh;

import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deliberate Redis-outage behavior of
 * {@link RedisRefreshTokenStore} ([RISK-36] P2 fix): only {@code issue} fails closed
 * (a token that could not be stored must not be handed out — the login path maps the
 * propagation to 503 {@code service_unavailable}); every read/revoke path degrades
 * to empty/false/Unknown so a Redis blip never 500s auth or session management.
 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    private StringRedisTemplate redis;

    private final JwtCookieProperties props =
            new JwtCookieProperties(null, null, null, 7L, null, null, null);

    @Test
    void rotateWithRedisDownReportsUnknown() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.rotate("some-token")).isInstanceOf(RotationResult.Unknown.class);
    }

    @Test
    void issueWithRedisDownPropagatesFor503Mapping() {
        when(redis.opsForHash()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThatThrownBy(() -> store.issue(UUID.randomUUID(), "u@acme.com", "tenant_acme", null, null))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void listSessionsWithRedisDownReturnsEmpty() {
        when(redis.opsForSet()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.listSessions(UUID.randomUUID(), "tenant_acme")).isEmpty();
    }

    @Test
    void revokeSessionWithRedisDownReturnsFalse() {
        when(redis.opsForSet()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.revokeSession(UUID.randomUUID(), "tenant_acme", UUID.randomUUID())).isFalse();
    }

    @Test
    void revokeAllForUserWithRedisDownDoesNotThrow() {
        when(redis.opsForSet()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThatCode(() -> store.revokeAllForUser(UUID.randomUUID(), "tenant_acme"))
                .doesNotThrowAnyException();
    }

    @Test
    void activeSessionForWithRedisDownReturnsEmpty() {
        when(redis.opsForHash()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.activeSessionFor("some-token")).isEmpty();
    }

    @Test
    void revokeWithRedisDownReturnsWhatItManaged() {
        when(redis.opsForHash()).thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.revoke("some-token")).isFalse();
    }

    @Test
    void listAllSessionsWithRedisDownReturnsEmpty() {
        when(redis.scan(any(org.springframework.data.redis.core.ScanOptions.class)))
                .thenThrow(new DataAccessResourceFailureException("down"));
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, props);

        assertThat(store.listAllSessions("tenant_acme")).isEqualTo(List.of());
    }
}

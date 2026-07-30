package com.ibrhalil.forgesys;

import com.ibrhalil.forgesys.security.RedisTokenBlacklistService;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.security.refresh.IssuedRefresh;
import com.ibrhalil.forgesys.security.refresh.RedisRefreshTokenStore;
import com.ibrhalil.forgesys.security.refresh.RotationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RedisRefreshTokenStore} + {@link RedisTokenBlacklistService} against a
 * real Redis (K-34) — atomic rotation, reuse detection, revoke and the access-token
 * blacklist. The default H2 suite uses the in-memory stores; this class wires the Redis
 * implementations to a Testcontainers Redis instance directly.
 *
 * <p><strong>Gated:</strong> skipped unless {@code -Dforgesys.redis.it=true} is set, so
 * the default Docker-free build ({@code mvn clean install}) stays green. Run with:
 * <pre>{@code
 * ./mvnw -pl backend test -Dtest=RedisRefreshTokenIT -Dforgesys.redis.it=true
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "forgesys.redis.it", matches = "true")
class RedisRefreshTokenIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopRedis() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @Autowired
    private StringRedisTemplate redis;

    private RedisRefreshTokenStore store() {
        return new RedisRefreshTokenStore(redis, new JwtCookieProperties(null, null, null, 1L, null, null, null));
    }

    private RedisTokenBlacklistService blacklist() {
        return new RedisTokenBlacklistService(redis);
    }

    private final UUID userId = UUID.randomUUID();
    private static final String TENANT = "tenant_acme";

    @Test
    void issueAndRotateProducesANewToken() {
        RedisRefreshTokenStore store = store();
        IssuedRefresh first = store.issue(userId, "u@acme.com", TENANT);

        RotationResult result = store.rotate(first.token());

        assertThat(result).isInstanceOf(RotationResult.Rotated.class);
        assertThat(((RotationResult.Rotated) result).issued().token()).isNotEqualTo(first.token());
    }

    @Test
    void reusingAConsumedTokenSignalsReuse() {
        RedisRefreshTokenStore store = store();
        IssuedRefresh first = store.issue(userId, "u2@acme.com", TENANT);
        store.rotate(first.token());

        RotationResult result = store.rotate(first.token());

        assertThat(result).isInstanceOf(RotationResult.ReuseDetected.class);
    }

    @Test
    void revokeAllForUserKillsOutstandingTokens() {
        RedisRefreshTokenStore store = store();
        IssuedRefresh a = store.issue(userId, "u3@acme.com", TENANT);
        IssuedRefresh b = store.issue(userId, "u3@acme.com", TENANT);

        store.revokeAllForUser(userId, TENANT);

        assertThat(store.rotate(a.token())).isInstanceOf(RotationResult.Unknown.class);
        assertThat(store.rotate(b.token())).isInstanceOf(RotationResult.Unknown.class);
    }

    @Test
    void blacklistRejectsAccessTokensUntilExpiry() throws InterruptedException {
        RedisTokenBlacklistService blacklist = blacklist();
        String jti = UUID.randomUUID().toString();

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
        blacklist.blacklist(jti, 1);
        assertThat(blacklist.isBlacklisted(jti)).isTrue();

        // TTL=1s → entry expires shortly after.
        Thread.sleep(Duration.ofSeconds(2).toMillis());
        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }
}

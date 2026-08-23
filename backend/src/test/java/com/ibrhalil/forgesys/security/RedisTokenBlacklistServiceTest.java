package com.ibrhalil.forgesys.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deliberate Redis-outage behavior of
 * {@link RedisTokenBlacklistService} ([RISK-36] P2 fix): the blacklist is
 * defense-in-depth over signature + expiry + tokenInvalidBefore, so a Redis blip
 * must degrade (fail-open read, best-effort write) instead of 500-ing every
 * authenticated request or breaking logout.
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Test
    void blacklistWriteFailureIsBestEffort() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(redis);

        assertThatCode(() -> service.blacklist("jti-1", 900)).doesNotThrowAnyException();
    }

    @Test
    void blacklistCheckFailureFailsOpen() {
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(redis);

        assertThat(service.isBlacklisted("jti-1")).isFalse();
    }

    @Test
    void happyPathBlacklistsAndReports() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.hasKey("bl:jti:jti-1")).thenReturn(true);
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(redis);

        service.blacklist("jti-1", 900);

        org.mockito.Mockito.verify(valueOps).set("bl:jti:jti-1", "1", Duration.ofSeconds(900));
        assertThat(service.isBlacklisted("jti-1")).isTrue();
    }

    @Test
    void blankJtiIsIgnoredWithoutTouchingRedis() {
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(redis);

        assertThat(service.isBlacklisted(null)).isFalse();
        assertThat(service.isBlacklisted("  ")).isFalse();
        service.blacklist(null, 900);
        org.mockito.Mockito.verifyNoInteractions(redis);
    }
}

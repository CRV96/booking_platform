package com.booking.platform.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    /**
     * ValueOperations is an interface — can always be mocked.
     * StringRedisTemplate is a concrete Spring class that can't be mocked on Java 25+
     * without instrumentation, so we use an anonymous subclass stub instead.
     */
    @Mock
    private ValueOperations<String, String> valueOps;

    private final Map<String, Boolean> hasKeyStore = new HashMap<>();
    private StringRedisTemplate redisTemplate;
    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        redisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
            @Override
            public Boolean hasKey(String key) {
                return hasKeyStore.getOrDefault(key, false);
            }
        };
        service = new TokenBlacklistService(redisTemplate);
    }

    // ── blacklist() ───────────────────────────────────────────────────────────

    @Test
    void blacklist_storesKeyWithTtlInRedis() {
        Instant expiry = Instant.now().plusSeconds(300);

        service.blacklist("jti-abc", expiry);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq("jwt:blacklist:jti-abc"), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().getSeconds()).isGreaterThan(0).isLessThanOrEqualTo(300);
    }

    @Test
    void blacklist_doesNothingForNullJti() {
        service.blacklist(null, Instant.now().plusSeconds(300));

        verifyNoInteractions(valueOps);
    }

    @Test
    void blacklist_doesNothingForBlankJti() {
        service.blacklist("   ", Instant.now().plusSeconds(300));

        verifyNoInteractions(valueOps);
    }

    @Test
    void blacklist_doesNothingWhenTokenAlreadyExpired() {
        service.blacklist("jti-expired", Instant.now().minusSeconds(10));

        verifyNoInteractions(valueOps);
    }

    @Test
    void blacklist_usesCorrectRedisKeyPrefix() {
        service.blacklist("my-jti", Instant.now().plusSeconds(60));

        verify(valueOps).set(eq("jwt:blacklist:my-jti"), any(), any(Duration.class));
    }

    // ── isBlacklisted() ───────────────────────────────────────────────────────

    @Test
    void isBlacklisted_returnsTrueWhenKeyExistsInRedis() {
        hasKeyStore.put("jwt:blacklist:jti-abc", true);

        assertThat(service.isBlacklisted("jti-abc")).isTrue();
    }

    @Test
    void isBlacklisted_returnsFalseWhenKeyDoesNotExist() {
        assertThat(service.isBlacklisted("jti-abc")).isFalse();
    }

    @Test
    void isBlacklisted_returnsFalseWhenRedisReturnsNull() {
        hasKeyStore.put("jwt:blacklist:jti-null", null);

        assertThat(service.isBlacklisted("jti-null")).isFalse();
    }

    @Test
    void isBlacklisted_returnsFalseForNullJti() {
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    void isBlacklisted_returnsFalseForBlankJti() {
        assertThat(service.isBlacklisted("  ")).isFalse();
    }
}

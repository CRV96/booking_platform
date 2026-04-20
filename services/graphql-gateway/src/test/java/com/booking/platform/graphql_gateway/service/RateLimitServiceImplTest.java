package com.booking.platform.graphql_gateway.service;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import com.booking.platform.graphql_gateway.dto.RateLimitResult;
import com.booking.platform.graphql_gateway.service.impl.RateLimitServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private RateLimitServiceImpl rateLimitService;

    @SuppressWarnings("unchecked")
    private void stubRedis(long count, long ttl) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(count, ttl));
    }

    // ── Allowed ───────────────────────────────────────────────────────────────

    @Test
    void checkLimit_countBelowMax_returnsAllowed() {
        stubRedis(5L, 55L);

        RateLimitResult result = rateLimitService.checkLimit("user:u-1", 30, 60);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void checkLimit_countAtMax_returnsAllowed() {
        stubRedis(30L, 30L);

        RateLimitResult result = rateLimitService.checkLimit("user:u-1", 30, 60);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void checkLimit_allowed_returnsCorrectCounts() {
        stubRedis(10L, 50L);

        RateLimitResult result = rateLimitService.checkLimit("user:u-1", 30, 60);

        assertThat(result.currentCount()).isEqualTo(10);
        assertThat(result.limit()).isEqualTo(30);
        assertThat(result.remaining()).isEqualTo(20);
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    // ── Blocked ───────────────────────────────────────────────────────────────

    @Test
    void checkLimit_countAboveMax_returnsBlocked() {
        stubRedis(35L, 25L);

        RateLimitResult result = rateLimitService.checkLimit("ip:1.2.3.4", 30, 60);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void checkLimit_blocked_retryAfterEqualsTtl() {
        stubRedis(35L, 25L);

        RateLimitResult result = rateLimitService.checkLimit("ip:1.2.3.4", 30, 60);

        assertThat(result.retryAfterSeconds()).isEqualTo(25L);
    }

    @Test
    void checkLimit_blocked_retryAfterAtLeastOne_whenTtlIsZero() {
        stubRedis(35L, 0L);

        RateLimitResult result = rateLimitService.checkLimit("ip:1.2.3.4", 30, 60);

        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    // ── Redis key format ──────────────────────────────────────────────────────

    @Test
    void checkLimit_usesRateLimitKeyPrefix() {
        stubRedis(1L, 60L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        rateLimitService.checkLimit("user:u-1", 100, 60);

        verify(redisTemplate).execute(any(), keysCaptor.capture(), any());
        String key = keysCaptor.getValue().get(0);
        assertThat(key).startsWith(GatewayConstants.RateLimit.KEY_PREFIX);
        assertThat(key).contains("user:u-1");
    }

    @Test
    void checkLimit_keyIncludesWindowSegment() {
        stubRedis(1L, 60L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor1 = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor2 = ArgumentCaptor.forClass(List.class);

        rateLimitService.checkLimit("user:u-1", 100, 60);
        rateLimitService.checkLimit("user:u-2", 100, 60);

        verify(redisTemplate, times(2)).execute(any(), keysCaptor1.capture(), any());
        // Keys for different identities should be different
        List<List<String>> allKeys = keysCaptor1.getAllValues();
        assertThat(allKeys.get(0).get(0)).isNotEqualTo(allKeys.get(1).get(0));
    }

    // ── Redis failure — fail-open ─────────────────────────────────────────────

    @Test
    void checkLimit_redisThrows_allowsRequest() {
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RateLimitResult result = rateLimitService.checkLimit("user:u-1", 100, 60);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void checkLimit_redisThrows_zeroCount() {
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RuntimeException("timeout"));

        RateLimitResult result = rateLimitService.checkLimit("user:u-1", 100, 60);

        assertThat(result.currentCount()).isEqualTo(0);
    }
}

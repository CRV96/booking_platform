package com.booking.platform.graphql_gateway.service.impl;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import com.booking.platform.graphql_gateway.dto.RateLimitResult;
import com.booking.platform.graphql_gateway.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Redis-based rate limiter using a fixed-window counter.
 *
 * <p>Each key represents a (client, time-window) pair. A Lua script atomically
 * increments the counter and sets the TTL on first access, ensuring the window
 * expires cleanly without race conditions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

    /**
     * Lua script: INCR the key, set EXPIRE only if this is the first increment (count == 1).
     * Returns [currentCount, ttlRemaining].
     */
    private static final String LUA_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """;

    private static final DefaultRedisScript<List> REDIS_SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, List.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public RateLimitResult checkLimit(String key, int maxRequests, int windowSeconds) {
        String redisKey = GatewayConstants.RateLimit.KEY_PREFIX + key + ":" + currentWindow(windowSeconds);

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(
                    REDIS_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(windowSeconds));

            long count = result.get(0);
            long ttl = result.get(1);

            boolean allowed = count <= maxRequests;
            long retryAfter = allowed ? 0 : Math.max(ttl, 1);

            return new RateLimitResult(allowed, count, maxRequests, retryAfter);
        } catch (Exception e) {
            log.warn("Rate limit check failed for key '{}', allowing request: {}", key, e.getMessage());
            return new RateLimitResult(true, 0, maxRequests, 0);
        }
    }

    /**
     * Generates a window identifier based on the current epoch second divided by
     * the window size. This creates fixed-size buckets that auto-rotate.
     */
    private long currentWindow(int windowSeconds) {
        return Instant.now().getEpochSecond() / windowSeconds;
    }
}

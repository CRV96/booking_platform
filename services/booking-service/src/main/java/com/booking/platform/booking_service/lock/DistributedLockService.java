package com.booking.platform.booking_service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis-based distributed lock for serializing seat reservation operations.
 *
 * <p>Uses SET NX EX for atomic lock acquisition and a Lua script for
 * safe owner-only release. Lock keys are granular per event + seat category
 * so different categories never block each other.</p>
 *
 * <p>Key format: {@code lock:seat:{eventId}:{seatCategory}}</p>
 *
 * @see TokenBlacklistService for the existing Redis usage pattern in this codebase
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final String LOCK_PREFIX = "lock:seat:";

    /**
     * Lua script for atomic compare-and-delete (safe unlock).
     * Returns 1 if the lock was released, 0 if not owned by the caller.
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final DistributedLockProperties lockProperties;

    /**
     * Attempts to acquire a distributed lock for the given event and seat category.
     *
     * @param eventId      the event to lock
     * @param seatCategory the seat category to lock
     * @return a {@link LockHandle} if acquired, or {@code null} if the lock
     * could not be obtained after the configured number of retries
     */
    public LockHandle tryAcquire(String eventId, String seatCategory) {
        String lockKey = LOCK_PREFIX + eventId + ":" + seatCategory;
        String lockValue = UUID.randomUUID().toString();
        Duration ttl = lockProperties.getTtl();

        for (int attempt = 1; attempt <= lockProperties.getMaxRetries(); attempt++) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, ttl);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired: key='{}', value='{}', attempt={}",
                        lockKey, lockValue, attempt);
                return new LockHandle(lockKey, lockValue);
            }

            if (attempt < lockProperties.getMaxRetries()) {
                log.debug("Lock contention on '{}', retry {}/{}",
                        lockKey, attempt, lockProperties.getMaxRetries());
                sleep(lockProperties.getRetryDelay().toMillis() * attempt);
            }
        }

        log.warn("Failed to acquire lock after {} attempts: key='{}'",
                lockProperties.getMaxRetries(), lockKey);
        return null;
    }

    /**
     * Releases the lock only if still owned by this handle.
     * Uses a Lua script for atomic compare-and-delete to prevent
     * accidentally releasing another owner's lock.
     *
     * @param handle the lock handle returned by {@link #tryAcquire}
     */
    public void release(LockHandle handle) {
        if (handle == null) return;

        Long result = redisTemplate.execute(
                UNLOCK_REDIS_SCRIPT,
                List.of(handle.lockKey()),
                handle.lockValue()
        );

        if (Long.valueOf(1L).equals(result)) {
            log.debug("Lock released: key='{}'", handle.lockKey());
        } else {
            log.warn("Lock already expired or stolen: key='{}'", handle.lockKey());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted", e);
        }
    }
}

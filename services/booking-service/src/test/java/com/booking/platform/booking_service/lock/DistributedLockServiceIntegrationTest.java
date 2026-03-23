package com.booking.platform.booking_service.lock;

import com.booking.platform.booking_service.base.BaseIntegrationTest;
import com.booking.platform.booking_service.properties.DistributedLockProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DistributedLockService}.
 * Uses a real Redis instance via Testcontainers.
 */
class DistributedLockServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private DistributedLockProperties lockProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ─── Basic acquire and release ───────────────────────────────────

    @Test
    void acquireAndRelease_singleThread_succeeds() {
        LockHandle handle = lockService.tryAcquire("event-1", "VIP");

        assertThat(handle).isNotNull();
        assertThat(handle.lockKey()).isEqualTo("lock:seat:event-1:VIP");
        assertThat(handle.lockValue()).isNotBlank();

        // Key should exist in Redis
        assertThat(redisTemplate.hasKey(handle.lockKey())).isTrue();

        lockService.release(handle);

        // Key should be gone after release
        assertThat(redisTemplate.hasKey(handle.lockKey())).isFalse();
    }

    // ─── Exclusivity ─────────────────────────────────────────────────

    @Test
    void lock_isExclusive_secondAcquireReturnsNull() {
        LockHandle first = lockService.tryAcquire("event-2", "GA");
        assertThat(first).isNotNull();

        // Second acquire on the same key should fail (retries exhausted)
        LockHandle second = lockService.tryAcquire("event-2", "GA");
        assertThat(second).isNull();

        lockService.release(first);
    }

    // ─── Auto-expiry ────────────────────────────────────────────────

    @Test
    void lock_autoExpires_afterTtl() throws InterruptedException {
        // Override TTL to 1 second for this test
        Duration originalTtl = lockProperties.getTtl();
        lockProperties.setTtl(Duration.ofSeconds(1));

        try {
            LockHandle handle = lockService.tryAcquire("event-3", "VIP");
            assertThat(handle).isNotNull();

            // Wait for TTL to expire
            Thread.sleep(1500);

            // Key should have auto-expired
            assertThat(redisTemplate.hasKey(handle.lockKey())).isFalse();

            // New acquire on the same key should succeed
            LockHandle newHandle = lockService.tryAcquire("event-3", "VIP");
            assertThat(newHandle).isNotNull();
            lockService.release(newHandle);
        } finally {
            lockProperties.setTtl(originalTtl);
        }
    }

    // ─── Different keys are independent ──────────────────────────────

    @Test
    void differentKeys_areIndependent() {
        LockHandle vip = lockService.tryAcquire("event-4", "VIP");
        LockHandle ga = lockService.tryAcquire("event-4", "GA");

        assertThat(vip).isNotNull();
        assertThat(ga).isNotNull();
        assertThat(vip.lockKey()).isNotEqualTo(ga.lockKey());

        lockService.release(vip);
        lockService.release(ga);
    }

    @Test
    void differentEvents_sameCategory_areIndependent() {
        LockHandle event1 = lockService.tryAcquire("event-5", "VIP");
        LockHandle event2 = lockService.tryAcquire("event-6", "VIP");

        assertThat(event1).isNotNull();
        assertThat(event2).isNotNull();

        lockService.release(event1);
        lockService.release(event2);
    }

    // ─── Release by non-owner fails ──────────────────────────────────

    @Test
    void releaseByNonOwner_doesNotDeleteLock() {
        LockHandle real = lockService.tryAcquire("event-7", "VIP");
        assertThat(real).isNotNull();

        // Create a fake handle with different lockValue
        LockHandle fake = new LockHandle(real.lockKey(), "fake-value-not-the-owner");
        lockService.release(fake);

        // Lock should still be held (non-owner release had no effect)
        assertThat(redisTemplate.hasKey(real.lockKey())).isTrue();

        // Real owner can still release
        lockService.release(real);
        assertThat(redisTemplate.hasKey(real.lockKey())).isFalse();
    }

    // ─── Retry succeeds when lock is released during backoff ─────────

    @Test
    void retrySucceeds_whenLockReleasedDuringBackoff() throws Exception {
        LockHandle first = lockService.tryAcquire("event-8", "GA");
        assertThat(first).isNotNull();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Schedule release after 150ms (during the second retry's backoff)
            executor.submit(() -> {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                lockService.release(first);
            });

            // This should retry and eventually succeed after the release
            LockHandle second = lockService.tryAcquire("event-8", "GA");
            assertThat(second).isNotNull();

            lockService.release(second);
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ─── Release null handle is safe ─────────────────────────────────

    @Test
    void releaseNullHandle_doesNotThrow() {
        // Should be a no-op, not throw
        lockService.release(null);
    }
}

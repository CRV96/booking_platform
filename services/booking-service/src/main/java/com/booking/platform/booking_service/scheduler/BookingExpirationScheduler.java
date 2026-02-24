package com.booking.platform.booking_service.scheduler;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.lock.DistributedLockService;
import com.booking.platform.booking_service.lock.LockHandle;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.booking_service.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that auto-cancels PENDING bookings whose hold timer has expired.
 *
 * <p>Runs at a fixed interval (default 30 seconds). A distributed Redis lock
 * ensures only one instance executes the cleanup across a multi-node deployment.
 * If the lock is already held, the tick is silently skipped.</p>
 *
 * <p>For each expired booking the scheduler:
 * <ol>
 *   <li>Sets status to CANCELLED with reason "HOLD_EXPIRED"</li>
 *   <li>Releases the reserved seats back to event-service (best-effort)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private static final String SCHEDULER_LOCK_KEY = "lock:scheduler:booking-expiration";

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final DistributedLockService lockService;
    private final BookingExpirationProperties properties;

    @Scheduled(fixedRateString = "${booking.expiration.interval:30000}")
    public void checkExpiredBookings() {
        processExpiredBookings();
    }

    /**
     * Core expiration logic, extracted for direct invocation in integration tests.
     */
    void processExpiredBookings() {
        LockHandle lock = lockService.tryAcquireOnce(SCHEDULER_LOCK_KEY, properties.getLockTtl());
        if (lock == null) {
            log.debug("Expiration scheduler skipped — another instance holds the lock");
            return;
        }

        try {
            List<BookingEntity> expired = bookingRepository.findExpiredHolds(Instant.now());

            if (expired.isEmpty()) {
                return;
            }

            int limit = Math.min(expired.size(), properties.getBatchSize());
            int processed = 0;

            for (int i = 0; i < limit; i++) {
                BookingEntity booking = expired.get(i);
                try {
                    bookingService.expireBooking(booking.getId());
                    processed++;
                } catch (Exception e) {
                    log.error("Failed to expire booking '{}': {}", booking.getId(), e.getMessage());
                }
            }

            log.info("Expiration scheduler completed: expired={}, total_found={}",
                    processed, expired.size());

        } finally {
            lockService.release(lock);
        }
    }
}

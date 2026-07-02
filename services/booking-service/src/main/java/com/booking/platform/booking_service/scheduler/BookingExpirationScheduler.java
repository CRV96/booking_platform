package com.booking.platform.booking_service.scheduler;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.properties.BookingExpirationProperties;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.event.Level;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that auto-cancels PENDING bookings whose hold timer has expired.
 *
 * <p>Runs at a fixed interval (default 30 seconds). ShedLock ensures only one
 * instance runs the cleanup across a multi-node deployment — the lock is backed
 * by the {@code shedlock} table in PostgreSQL (see V2__create_shedlock_table.sql).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingExpirationProperties properties;

    @Scheduled(fixedRateString = "${booking.expiration.interval:30000}")
    @SchedulerLock(
            name = "booking-expiration",
            lockAtMostFor = "${booking.expiration.lock-ttl:PT25S}",
            lockAtLeastFor = "PT10S"
    )
    public void checkExpiredBookings() {
        processExpiredBookings();
    }

    /**
     * Core expiration logic, extracted for direct invocation in integration tests.
     * ShedLock is applied at the {@link #checkExpiredBookings()} level and does not
     * wrap this method — tests can call it freely without holding a lock.
     */
    void processExpiredBookings() {
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
                ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.BOOKING_CANCELLATION_FAILED,
                        "Failed to expire booking '{}'", booking.getId(), e);
            }
        }

        ApplicationLogger.logMessage(log, Level.INFO, "Expiration scheduler completed: expired={}, total_found={}",
                processed, expired.size());
    }
}

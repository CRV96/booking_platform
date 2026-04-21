package com.booking.platform.booking_service.scheduler;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.lock.DistributedLockService;
import com.booking.platform.booking_service.lock.LockHandle;
import com.booking.platform.booking_service.properties.BookingExpirationProperties;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.booking_service.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingExpirationSchedulerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;
    @Mock private DistributedLockService lockService;
    @Mock private BookingExpirationProperties properties;

    @InjectMocks private BookingExpirationScheduler scheduler;

    private static final LockHandle LOCK = new LockHandle("lock:scheduler:booking-expiration", "token");

    @BeforeEach
    void setUp() {
        when(properties.getLockTtl()).thenReturn(Duration.ofSeconds(25));
        when(properties.getBatchSize()).thenReturn(100);
    }

    private BookingEntity pendingBooking(UUID id) {
        return BookingEntity.builder()
                .id(id)
                .userId("u-1")
                .eventId("ev-1")
                .eventTitle("Fest")
                .status(BookingStatus.PENDING)
                .seatCategory("GA")
                .quantity(1)
                .unitPrice(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("10.00"))
                .currency("USD")
                .idempotencyKey("key-" + id)
                .holdExpiresAt(Instant.now().minus(Duration.ofMinutes(1)))
                .build();
    }

    // ── lock not acquired ─────────────────────────────────────────────────────

    @Test
    void processExpiredBookings_lockNotAcquired_skipsProcessing() {
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(null);

        scheduler.processExpiredBookings();

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingService);
    }

    // ── no expired bookings ───────────────────────────────────────────────────

    @Test
    void processExpiredBookings_noExpired_doesNotCallService() {
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any())).thenReturn(List.of());

        scheduler.processExpiredBookings();

        verifyNoInteractions(bookingService);
        verify(lockService).release(LOCK);
    }

    // ── normal processing ─────────────────────────────────────────────────────

    @Test
    void processExpiredBookings_oneExpired_expiresIt() {
        UUID id = UUID.randomUUID();
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any())).thenReturn(List.of(pendingBooking(id)));

        scheduler.processExpiredBookings();

        verify(bookingService).expireBooking(id);
        verify(lockService).release(LOCK);
    }

    @Test
    void processExpiredBookings_multipleExpired_expiresAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any()))
                .thenReturn(List.of(pendingBooking(id1), pendingBooking(id2)));

        scheduler.processExpiredBookings();

        verify(bookingService).expireBooking(id1);
        verify(bookingService).expireBooking(id2);
    }

    // ── batch size limit ──────────────────────────────────────────────────────

    @Test
    void processExpiredBookings_moreExpiredThanBatchSize_processesOnlyBatch() {
        when(properties.getBatchSize()).thenReturn(2);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any()))
                .thenReturn(List.of(pendingBooking(id1), pendingBooking(id2), pendingBooking(id3)));

        scheduler.processExpiredBookings();

        verify(bookingService).expireBooking(id1);
        verify(bookingService).expireBooking(id2);
        verify(bookingService, never()).expireBooking(id3);
    }

    // ── failure on one booking ────────────────────────────────────────────────

    @Test
    void processExpiredBookings_oneBookingFails_continuesWithOthers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any()))
                .thenReturn(List.of(pendingBooking(id1), pendingBooking(id2)));
        doThrow(new RuntimeException("DB error")).when(bookingService).expireBooking(id1);

        scheduler.processExpiredBookings(); // should not propagate

        verify(bookingService).expireBooking(id1);
        verify(bookingService).expireBooking(id2); // second one still processed
    }

    // ── lock always released ──────────────────────────────────────────────────

    @Test
    void processExpiredBookings_exceptionDuringProcessing_lockAlwaysReleased() {
        when(lockService.tryAcquireOnce(anyString(), any())).thenReturn(LOCK);
        when(bookingRepository.findExpiredHolds(any())).thenThrow(new RuntimeException("DB down"));

        try {
            scheduler.processExpiredBookings();
        } catch (RuntimeException ignored) {}

        verify(lockService).release(LOCK);
    }
}

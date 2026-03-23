package com.booking.platform.booking_service.scheduler;

import com.booking.platform.booking_service.base.BaseIntegrationTest;
import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import com.booking.platform.booking_service.grpc.client.EventServiceClient;
import com.booking.platform.booking_service.repository.BookingRepository;
import com.booking.platform.common.grpc.event.UpdateSeatAvailabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link BookingExpirationScheduler}.
 * Uses real PostgreSQL and Redis via Testcontainers.
 * Event-service gRPC calls are mocked.
 */
class BookingExpirationSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BookingExpirationScheduler scheduler;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private EventServiceClient eventServiceClient;

    @BeforeEach
    void setupMocks() {
        when(eventServiceClient.updateSeatAvailability(anyString(), anyString(), anyInt()))
                .thenReturn(UpdateSeatAvailabilityResponse.newBuilder()
                        .setSuccess(true)
                        .setRemainingSeats(100)
                        .build());
    }

    // ─── Expired bookings are cancelled ──────────────────────────────

    @Test
    void processExpiredBookings_expiredPendingBooking_cancelsWithHoldExpiredReason() {
        BookingEntity expired = saveBooking(
                BookingStatus.PENDING,
                Instant.now().minus(5, ChronoUnit.MINUTES)  // hold expired 5 min ago
        );

        scheduler.processExpiredBookings();

        BookingEntity updated = bookingRepository.findById(expired.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.getCancellationReason()).isEqualTo("HOLD_EXPIRED");
    }

    // ─── Non-expired bookings are not touched ────────────────────────

    @Test
    void processExpiredBookings_nonExpiredPendingBooking_remainsPending() {
        BookingEntity active = saveBooking(
                BookingStatus.PENDING,
                Instant.now().plus(5, ChronoUnit.MINUTES)   // hold expires in 5 min
        );

        scheduler.processExpiredBookings();

        BookingEntity updated = bookingRepository.findById(active.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ─── Seats are released on expiration ────────────────────────────

    @Test
    void processExpiredBookings_releasesSeatsViaEventService() {
        BookingEntity expired = saveBooking(
                BookingStatus.PENDING,
                Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        scheduler.processExpiredBookings();

        // Verify seats released: positive delta = release
        verify(eventServiceClient).updateSeatAvailability(
                expired.getEventId(), expired.getSeatCategory(), expired.getQuantity());
    }

    // ─── Already cancelled bookings are skipped ──────────────────────

    @Test
    void processExpiredBookings_alreadyCancelledBooking_noDoubleRelease() {
        BookingEntity cancelled = saveBooking(
                BookingStatus.CANCELLED,
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );

        scheduler.processExpiredBookings();

        // Should not be picked up (query only finds PENDING bookings)
        verify(eventServiceClient, never()).updateSeatAvailability(
                eq(cancelled.getEventId()), eq(cancelled.getSeatCategory()), anyInt());
    }

    // ─── Multiple expired bookings processed ─────────────────────────

    @Test
    void processExpiredBookings_multipleExpired_allCancelled() {
        BookingEntity expired1 = saveBooking(
                BookingStatus.PENDING,
                Instant.now().minus(10, ChronoUnit.MINUTES)
        );
        BookingEntity expired2 = saveBooking(
                BookingStatus.PENDING,
                Instant.now().minus(3, ChronoUnit.MINUTES)
        );
        BookingEntity active = saveBooking(
                BookingStatus.PENDING,
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );

        scheduler.processExpiredBookings();

        assertThat(bookingRepository.findById(expired1.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(expired2.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    // ─── Seat release failure does not prevent cancellation ──────────

    @Test
    void processExpiredBookings_seatReleaseFails_bookingStillCancelled() {
        doThrow(new RuntimeException("Event-service unavailable"))
                .when(eventServiceClient)
                .updateSeatAvailability(anyString(), anyString(), anyInt());

        BookingEntity expired = saveBooking(
                BookingStatus.PENDING,
                Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        scheduler.processExpiredBookings();

        BookingEntity updated = bookingRepository.findById(expired.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(updated.getCancellationReason()).isEqualTo("HOLD_EXPIRED");
    }

    // ─── No expired bookings — no-op ─────────────────────────────────

    @Test
    void processExpiredBookings_noExpiredBookings_doesNothing() {
        saveBooking(BookingStatus.PENDING, Instant.now().plus(10, ChronoUnit.MINUTES));

        scheduler.processExpiredBookings();

        verify(eventServiceClient, never()).updateSeatAvailability(anyString(), anyString(), anyInt());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private BookingEntity saveBooking(BookingStatus status, Instant holdExpiresAt) {
        BookingEntity booking = BookingEntity.builder()
                .userId("user-" + UUID.randomUUID().toString().substring(0, 8))
                .eventId("event-" + UUID.randomUUID().toString().substring(0, 8))
                .eventTitle("Test Concert")
                .status(status)
                .seatCategory("VIP")
                .quantity(2)
                .unitPrice(new BigDecimal("49.99"))
                .totalPrice(new BigDecimal("99.98"))
                .currency("USD")
                .idempotencyKey(UUID.randomUUID().toString())
                .holdExpiresAt(holdExpiresAt)
                .build();

        return bookingRepository.save(booking);
    }
}

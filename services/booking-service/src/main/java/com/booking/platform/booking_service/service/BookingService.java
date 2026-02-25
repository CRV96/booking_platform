package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.BookingEntity;
import org.springframework.data.domain.Page;

import java.util.UUID;

/**
 * Business logic for booking operations.
 * Coordinates between Redis locks, PostgreSQL persistence,
 * and event-service gRPC calls.
 */
public interface BookingService {

    /**
     * Creates a new booking with distributed lock protection.
     * Flow: acquire lock → idempotency check → validate event via gRPC →
     * decrement seats → persist PENDING booking → release lock.
     */
    BookingEntity createBooking(String userId, String eventId,
                                String seatCategory, int quantity, String idempotencyKey);

    BookingEntity getBooking(UUID bookingId, String userId);

    Page<BookingEntity> getUserBookings(String userId, int page,
                                        int pageSize, String statusFilter);

    BookingEntity cancelBooking(UUID bookingId, String userId, String reason);

    /**
     * Confirms a PENDING booking after successful payment.
     * Transitions status from PENDING → CONFIRMED and publishes a
     * {@code BookingConfirmedEvent} to Kafka.
     *
     * <p>Called by {@link com.booking.platform.booking_service.messaging.consumer.PaymentEventConsumer}
     * when a {@code PaymentCompletedEvent} is received from payment-service.</p>
     *
     * <p>Idempotent: if the booking is already CONFIRMED, returns it as-is.</p>
     *
     * @throws com.booking.platform.booking_service.exception.BookingNotFoundException if booking does not exist
     */
    BookingEntity confirmBooking(UUID bookingId);

    /**
     * System-level expiration of a PENDING booking whose hold timer has elapsed.
     * Unlike {@link #cancelBooking}, this does not require a userId — it is called
     * by the scheduled expiration job, not by a user action.
     *
     * <p>Idempotent: silently skips bookings that no longer exist or are no longer PENDING.</p>
     */
    void expireBooking(UUID bookingId);

    /**
     * Cancels a booking after payment failure (P3-07 compensation).
     * Transitions PENDING → CANCELLED, releases seats, and publishes BookingCancelledEvent.
     *
     * <p>Idempotent: silently skips if not PENDING (already cancelled/confirmed).</p>
     *
     * @param bookingId the booking to cancel
     * @param reason    the payment failure reason (e.g. "Card declined")
     */
    void cancelBookingOnPaymentFailure(UUID bookingId, String reason);
}

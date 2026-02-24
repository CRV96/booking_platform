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
     * System-level expiration of a PENDING booking whose hold timer has elapsed.
     * Unlike {@link #cancelBooking}, this does not require a userId — it is called
     * by the scheduled expiration job, not by a user action.
     *
     * <p>Idempotent: silently skips bookings that no longer exist or are no longer PENDING.</p>
     */
    void expireBooking(UUID bookingId);
}

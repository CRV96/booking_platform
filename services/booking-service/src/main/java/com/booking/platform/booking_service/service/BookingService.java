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
}

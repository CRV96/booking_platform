package com.booking.platform.booking_service.constants;

/**
 * Constants for JPA entity field names, Redis key prefixes, and domain-specific
 * string literals used across the booking-service.
 *
 * <p>Centralizes all raw strings so that typos are caught at compile time
 * and renames only need to happen in one place.
 */
public final class EntityConst {
    private EntityConst() {}

    /** Constants for the {@code bookings} table / {@link com.booking.platform.booking_service.entity.BookingEntity}. */
    public static final class Booking {
        private Booking() {}

        public static final String CREATED_AT = "createdAt";
    }

    /** Cancellation reason constants used when system-cancelling bookings. */
    public static final class CancellationReason {
        private CancellationReason() {}

        public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
        public static final String PAYMENT_FAILED_PREFIX = "PAYMENT_FAILED: ";
    }

    /** Event status values received from event-service gRPC responses. */
    public static final class EventStatus {
        private EventStatus() {}

        public static final String PUBLISHED = "PUBLISHED";
    }

    /** Redis key prefixes and lock keys. */
    public static final class RedisKeys {
        private RedisKeys() {}

        public static final String SEAT_LOCK_PREFIX = "lock:seat:";
        public static final String SCHEDULER_LOCK_BOOKING_EXPIRATION = "lock:scheduler:booking-expiration";
    }
}

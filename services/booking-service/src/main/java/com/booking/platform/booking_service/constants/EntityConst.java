package com.booking.platform.booking_service.constants;

/**
 * Constants for JPA entity field names used in queries and sort operations.
 *
 * <p>Centralizes all raw field-name strings so that typos are caught at
 * compile time and renames only need to happen in one place.
 */
public final class EntityConst {
    private EntityConst() {}

    /** Constants for the {@code bookings} table / {@link com.booking.platform.booking_service.entity.BookingEntity}. */
    public static final class Booking {
        private Booking() {}

        public static final String CREATED_AT = "createdAt";
    }
}

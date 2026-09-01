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

        public static final String TABLE_NAME = "bookings";

        public static final String ID = "id";
        public static final String USER_ID = "user_id";
        public static final String EVENT_ID = "event_id";
        public static final String EVENT_TITLE = "event_title";
        public static final String STATUS = "status";
        public static final String SEAT_CATEGORY = "seat_category";
        public static final String QUANTITY = "quantity";
        public static final String UNIT_PRICE = "unit_price";
        public static final String TOTAL_PRICE = "total_price";
        public static final String CURRENCY = "currency";
        public static final String IDEMPOTENCY_KEY = "idempotency_key";
        public static final String HOLD_EXPIRES_AT = "hold_expires_at";
        public static final String CANCELLATION_REASON = "cancellation_reason";
        public static final String CREATED_AT = "createdAt";
        public static final String CREATED_AT_COLUMN = "created_at";
        public static final String UPDATED_AT = "updated_at";
        public static final String VERSION = "version";

        public static final String IDX_USER_ID = "idx_bookings_user_id";
        public static final String IDX_EVENT_ID = "idx_bookings_event_id";
        public static final String IDX_STATUS = "idx_bookings_status";
    }

    /** Constants for the {@code cart_items} table / {@link com.booking.platform.booking_service.entity.CartItemEntity}. */
    public static final class CartItem {
        private CartItem() {}

        public static final String TABLE_NAME = "cart_items";

        public static final String ID = "id";
        public static final String USER_ID = "user_id";
        public static final String EVENT_ID = "event_id";
        public static final String EVENT_TITLE = "event_title";
        public static final String SEAT_CATEGORY = "seat_category";
        public static final String QUANTITY = "quantity";
        public static final String UNIT_PRICE = "unit_price";
        public static final String CURRENCY = "currency";
        public static final String CREATED_AT_COLUMN = "created_at";
        public static final String UPDATED_AT = "updated_at";
        public static final String VERSION = "version";

        public static final String IDX_USER_ID = "idx_cart_items_user_id";
        public static final String UQ_USER_EVENT_CATEGORY = "uq_cart_user_event_category";
    }

    /** Constants for the {@code favorites} table / {@link com.booking.platform.booking_service.entity.FavoriteEntity}. */
    public static final class Favorite {
        private Favorite() {}

        public static final String TABLE_NAME = "favorites";

        public static final String ID = "id";
        public static final String USER_ID = "user_id";
        public static final String EVENT_ID = "event_id";
        public static final String CREATED_AT_COLUMN = "created_at";

        public static final String IDX_USER_ID = "idx_favorites_user_id";
        public static final String UQ_USER_EVENT = "uq_favorite_user_event";
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
    }
}

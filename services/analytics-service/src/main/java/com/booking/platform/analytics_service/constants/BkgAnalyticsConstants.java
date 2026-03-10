package com.booking.platform.analytics_service.constants;

/**
 * Centralized constants for analytics-service.
 *
 * <p>Includes:
 * - Payload keys for event data extraction.
 * - Kafka topic and event names for booking and payment analytics.
 *
 * <p>Organized into nested classes for clarity:
 * - {@code BkgPaymentConstants} for payment-related events.
 * - {@code BkgEventConstants} for event-related events.
 * - {@code BkgBookingConstants} for booking-related events.
 */
public final class BkgAnalyticsConstants {
    private BkgAnalyticsConstants() {}

    // Shared payload / document field names
    public static final String PAYLOAD_EVENT_ID = "eventId";
    public static final String PAYLOAD_EVENT_TITLE = "eventTitle";
    public static final String PAYLOAD_BOOKING_ID = "bookingId";
    public static final String PAYLOAD_CURRENCY = "currency";
    public static final String PAYLOAD_REASON = "reason";

    // Payment-specific payload keys
    public static final String PAYLOAD_PAYMENT_ID = "paymentId";
    public static final String PAYLOAD_AMOUNT = "amount";
    public static final String PAYLOAD_REFUND_ID = "refundId";

    // Document field names (shared across collections)
    public static final String LAST_UPDATED = "lastUpdated";
    public static final String CATEGORY = "category";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TITLE = "eventTitle";
    public static final String CURRENCY = "currency";
    public static final String DATE = "date";

    public final class BkgPaymentConstants {
        private BkgPaymentConstants() {}

        public static final String PAYMENT_COMPLETED_FACTORY = "paymentCompletedListenerFactory";
        public static final String PAYMENT_COMPLETED_EVENT = "paymentCompletedEvent";
        public static final String PAYMENT_COMPLETED_TOPIC = "paymentsCompleted";

        public static final String PAYMENT_FAILED_FACTORY = "paymentFailedListenerFactory";
        public static final String PAYMENT_FAILED_EVENT = "paymentFailedEvent";
        public static final String PAYMENT_FAILED_TOPIC = "paymentFailed";

        public static final String PAYMENT_REFUND_FACTORY = "refundCompletedListenerFactory";
        public static final String PAYMENT_REFUND_EVENT = "paymentRefundEvent";
        public static final String PAYMENT_REFUND_COMPLETED = "refundsCompleted";
        public static final String PAYMENT_REFUND_TOTAL_REFUNDS = "totalRefunds";

        // daily_metrics document field names
        public static final String PAYMENTS_COMPLETED = "paymentsCompleted";
        public static final String PAYMENTS_FAILED = "paymentsFailed";
    }

    public final class BkgEventConstants {
        private BkgEventConstants() {}

        public static final String EVENT_CREATED_FACTORY = "eventCreatedListenerFactory";
        public static final String EVENT_CREATED_EVENT = "EventCreatedEvent";
        public static final String EVENTS_CREATED = "eventsCreated";

        public static final String EVENT_UPDATED_FACTORY = "eventUpdatedListenerFactory";
        public static final String EVENT_UPDATED_EVENT = "EventUpdatedEvent";

        public static final String EVENT_PUBLISHED_FACTORY = "eventPublishedListenerFactory";
        public static final String EVENT_PUBLISHED_EVENT = "EventPublishedEvent";
        public static final String EVENTS_PUBLISHED = "eventsPublished";

        public static final String EVENT_CANCELLED_FACTORY = "eventCancelledListenerFactory";
        public static final String EVENT_CANCELLED_EVENT = "EventCancelledEvent";
        public static final String EVENTS_CANCELLED = "eventsCancelled";

        public static final String TOTAL_EVENTS = "totalEvents";
        public static final String PUBLISHED_EVENTS = "publishedEvents";

        public static final String PAYLOAD_TITLE = "title";
        public static final String PAYLOAD_CATEGORY = "category";
        public static final String PAYLOAD_CHANGED_FIELDS = "changedFields";
    }

    public final class BkgBookingConstants {
        private BkgBookingConstants() {}

        public static final String BOOKING_CREATED_FACTORY = "bookingCreatedListenerFactory";
        public static final String BOOKING_CREATED_EVENT = "BookingCreatedEvent";
        public static final String BOOKINGS_CREATED = "bookingsCreated";

        public static final String BOOKING_CONFIRMED_FACTORY = "bookingConfirmedListenerFactory";
        public static final String BOOKING_CONFIRMED_EVENT = "BookingConfirmedEvent";
        public static final String BOOKINGS_CONFIRMED = "bookingsConfirmed";

        public static final String BOOKING_CANCELLED_FACTORY = "bookingCancelledListenerFactory";
        public static final String BOOKING_CANCELLED_EVENT = "BookingCancelledEvent";
        public static final String BOOKINGS_CANCELLED = "bookingsCancelled";

        public static final String TOTAL_BOOKINGS = "totalBookings";
        public static final String CONFIRMED_BOOKINGS = "confirmedBookings";
        public static final String CANCELLED_BOOKINGS = "cancelledBookings";
        public static final String TOTAL_REVENUE = "totalRevenue";

        public static final String PAYLOAD_TOTAL_PRICE = "totalPrice";
        public static final String PAYLOAD_SEAT_CATEGORY = "seatCategory";
    }

    public final class BkgControllerConstants {
        private BkgControllerConstants() {}

        public static final String ANALYTICS_BASE_PATH = "/api/analytics";
        public static final String TOP_REVENUE_PATH = "/top-revenue";
        public static final String BOOKING_TRENDS_PATH = "/booking-trends";
        public static final String REVENUE_BY_CATEGORY_PATH = "/revenue-by-category";
        public static final String CANCELLATION_RATE_PATH = "/cancellation-rate";
        public static final String AVG_BOOKING_VALUE_PATH = "/avg-booking-value";

        public static final String PARAM_LIMIT = "limit";
        public static final String DEFAULT_LIMIT = "10";

        public static final String PARAM_DAYS = "days";
        public static final String DEFAULT_DAYS = "30";

        public static final String ALL_EVENTS_PATH = "/events";
        public static final String EVENT_ANALYTICS_PATH = "/event/{eventId}";
        public static final String PAYMENT_TRENDS_PATH = "/payment-trends";
        public static final String EVENT_LIFECYCLE_PATH = "/event-lifecycle";

        public static final String PARAM_EVENT_ID = "eventId";
    }

    public final class BkgDocumentConstants {
        private BkgDocumentConstants() {}

        public static final String DAILY_METRICS_COLLECTION = "daily_metrics";
        public static final String CATEGORY_STATS_COLLECTION = "category_stats";
        public static final String EVENT_LOG_COLLECTION = "event_log";
        public static final String EVENT_STATS_COLLECTION = "event_stats";
    }
}

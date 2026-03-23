package com.booking.platform.analytics_service.constants;

/**
 * Centralized constants for analytics-service.
 *
 * <p>Only values that are shared between 2+ classes (consumers, processors,
 * query service) are extracted here. Single-use aggregation aliases, SpEL
 * cache keys, and query expressions belong inline in the code that uses them.
 *
 * <p>Organized into nested classes by domain:
 * <ul>
 *   <li>{@link Payment}  — payment lifecycle Kafka config and document fields</li>
 *   <li>{@link Event}    — event lifecycle Kafka config and document fields</li>
 *   <li>{@link Booking}  — booking lifecycle Kafka config and document fields</li>
 *   <li>{@link Api}      — REST endpoint paths and parameter names</li>
 *   <li>{@link Collection} — MongoDB collection names</li>
 * </ul>
 */
public final class AnalyticsConstants {
    private AnalyticsConstants() {}

    // ── Shared payload keys (used when building raw event_log entries) ───────

    public static final String PAYLOAD_EVENT_ID = "eventId";
    public static final String PAYLOAD_EVENT_TITLE = "eventTitle";
    public static final String PAYLOAD_BOOKING_ID = "bookingId";
    public static final String PAYLOAD_CURRENCY = "currency";
    public static final String PAYLOAD_REASON = "reason";
    public static final String PAYLOAD_PAYMENT_ID = "paymentId";
    public static final String PAYLOAD_AMOUNT = "amount";
    public static final String PAYLOAD_REFUND_ID = "refundId";

    // ── Shared document field names (used across multiple collections) ───────

    public static final String LAST_UPDATED = "lastUpdated";
    public static final String CATEGORY = "category";
    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TITLE = "eventTitle";
    public static final String CURRENCY = "currency";
    public static final String DATE = "date";

    // ── Domain-specific inner classes ────────────────────────────────────────

    /** Payment lifecycle — Kafka config and {@code daily_metrics} fields. */
    public static final class Payment {
        private Payment() {}

        // Kafka listener factory bean names
        public static final String COMPLETED_FACTORY = "paymentCompletedListenerFactory";
        public static final String FAILED_FACTORY = "paymentFailedListenerFactory";
        public static final String REFUND_FACTORY = "refundCompletedListenerFactory";

        // Event type names (stored in event_log.eventType)
        public static final String COMPLETED_EVENT = "paymentCompletedEvent";
        public static final String FAILED_EVENT = "paymentFailedEvent";
        public static final String REFUND_EVENT = "paymentRefundEvent";

        // Document field names (daily_metrics — written by processor, read by query service)
        public static final String PAYMENTS_COMPLETED = "paymentsCompleted";
        public static final String PAYMENTS_FAILED = "paymentsFailed";
        public static final String REFUNDS_COMPLETED = "refundsCompleted";
        public static final String TOTAL_REFUNDS = "totalRefunds";
    }

    /** Event lifecycle — Kafka config and document fields. */
    public static final class Event {
        private Event() {}

        // Kafka listener factory bean names
        public static final String CREATED_FACTORY = "eventCreatedListenerFactory";
        public static final String UPDATED_FACTORY = "eventUpdatedListenerFactory";
        public static final String PUBLISHED_FACTORY = "eventPublishedListenerFactory";
        public static final String CANCELLED_FACTORY = "eventCancelledListenerFactory";

        // Event type names (stored in event_log.eventType)
        public static final String CREATED_EVENT = "EventCreatedEvent";
        public static final String UPDATED_EVENT = "EventUpdatedEvent";
        public static final String PUBLISHED_EVENT = "EventPublishedEvent";
        public static final String CANCELLED_EVENT = "EventCancelledEvent";

        // Document field names (daily_metrics — event lifecycle counters)
        public static final String EVENTS_CREATED = "eventsCreated";
        public static final String EVENTS_PUBLISHED = "eventsPublished";
        public static final String EVENTS_CANCELLED = "eventsCancelled";

        // Document field names (event_stats / category_stats)
        public static final String TOTAL_EVENTS = "totalEvents";
        public static final String PUBLISHED_EVENTS = "publishedEvents";

        // Payload keys specific to event messages
        public static final String PAYLOAD_TITLE = "title";
        public static final String PAYLOAD_CATEGORY = "category";
        public static final String PAYLOAD_CHANGED_FIELDS = "changedFields";
    }

    /** Booking lifecycle — Kafka config and document fields. */
    public static final class Booking {
        private Booking() {}

        // Kafka listener factory bean names
        public static final String CREATED_FACTORY = "bookingCreatedListenerFactory";
        public static final String CONFIRMED_FACTORY = "bookingConfirmedListenerFactory";
        public static final String CANCELLED_FACTORY = "bookingCancelledListenerFactory";

        // Event type names (stored in event_log.eventType)
        public static final String CREATED_EVENT = "BookingCreatedEvent";
        public static final String CONFIRMED_EVENT = "BookingConfirmedEvent";
        public static final String CANCELLED_EVENT = "BookingCancelledEvent";

        // Document field names (daily_metrics — booking lifecycle counters)
        public static final String BOOKINGS_CREATED = "bookingsCreated";
        public static final String BOOKINGS_CONFIRMED = "bookingsConfirmed";
        public static final String BOOKINGS_CANCELLED = "bookingsCancelled";

        // Document field names (event_stats — per-event counters)
        public static final String TOTAL_BOOKINGS = "totalBookings";
        public static final String CONFIRMED_BOOKINGS = "confirmedBookings";
        public static final String CANCELLED_BOOKINGS = "cancelledBookings";
        public static final String TOTAL_REVENUE = "totalRevenue";

        // Payload keys specific to booking messages
        public static final String PAYLOAD_TOTAL_PRICE = "totalPrice";
        public static final String PAYLOAD_SEAT_CATEGORY = "seatCategory";
    }

    /** REST controller paths and parameter names. */
    public static final class Api {
        private Api() {}

        public static final String ANALYTICS_BASE_PATH = "/api/analytics";
        public static final String TOP_REVENUE_PATH = "/top-revenue";
        public static final String BOOKING_TRENDS_PATH = "/booking-trends";
        public static final String REVENUE_BY_CATEGORY_PATH = "/revenue-by-category";
        public static final String CANCELLATION_RATE_PATH = "/cancellation-rate";
        public static final String AVG_BOOKING_VALUE_PATH = "/avg-booking-value";
        public static final String ALL_EVENTS_PATH = "/events";
        public static final String EVENT_ANALYTICS_PATH = "/event/{eventId}";
        public static final String PAYMENT_TRENDS_PATH = "/payment-trends";
        public static final String EVENT_LIFECYCLE_PATH = "/event-lifecycle";

        public static final String PARAM_LIMIT = "limit";
        public static final String DEFAULT_LIMIT = "10";
        public static final String PARAM_DAYS = "days";
        public static final String DEFAULT_DAYS = "30";
        public static final String PARAM_EVENT_ID = "eventId";
    }

    /** MongoDB collection names. */
    public static final class Collection {
        private Collection() {}

        public static final String DAILY_METRICS = "daily_metrics";
        public static final String CATEGORY_STATS = "category_stats";
        public static final String EVENT_LOG = "event_log";
        public static final String EVENT_STATS = "event_stats";
    }
}

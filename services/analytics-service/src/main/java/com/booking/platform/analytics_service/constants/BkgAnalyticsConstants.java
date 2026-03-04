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
    public static final String PAYLOAD_LAST_UPDATED = "lastUpdated";

    public final class BkgPaymentConstants {
        private BkgPaymentConstants() {}

        public static final String PAYMENT_COMPLETED_FACTORY = "paymentCompletedListenerFactory";
        public static final String PAYMENT_COMPLETED_EVENT = "paymentCompletedEvent";
        public static final String PAYMENT_COMPLETED_TOPIC = "paymentsCompleted";

        public static final String PAYMENT_FAILED_FACTORY = "paymentFailedListenerFactory";
        public static final String PAYMENT_FAILED_EVENT = "paymentFailedEvent";
        public static final String PAYMENT_FAILED_TOPIC = "paymentFailed";

        public static final String PAYMENT_REFUND_FACTORY = "paymentRefundListenerFactory";
        public static final String PAYMENT_REFUND_EVENT = "paymentRefundEvent";
        public static final String PAYMENT_REFUND_COMPLETED = "refundsCompleted";
        public static final String PAYMENT_REFUND_TOTAL_REFUNDS = "totalRefunds";
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
}

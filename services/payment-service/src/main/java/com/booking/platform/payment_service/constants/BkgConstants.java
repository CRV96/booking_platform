package com.booking.platform.payment_service.constants;

/**
 * Centralized constants for the Payment Service.
 *
 * <p>Includes nested classes for grouping related constants, such as those specific to Stripe integration.
 */
public final class BkgConstants {

    private BkgConstants() {
        // Private constructor to prevent instantiation
    }

    public final class BkgStripeConstants {
        private BkgStripeConstants(){}

        // Used in configuration properties and as a prefix for Stripe-related constants
        public static final String STRIPE = "stripe";
        public static final String PAYMENT_GATEWAY_TYPE= "payment.gateway.type";
        public static final String RESPONSE_SUCCEEDED = "succeeded";
        public static final String CARD_PAYMENT_METHOD = "card";
    }

    public final class BkgOutboxConstants {

        private BkgOutboxConstants(){}
        //
        public static final String ID = "id";
        public static final String TABLE_NAME = "outbox_events";
        public static final String AGGREGATE_TYPE = "aggregate_type";
        public static final String AGGREGATE_ID = "aggregate_id";
        public static final String EVENT_TYPE = "event_type";
        public static final String PAYLOAD = "payload";
        public static final String PAYLOAD_COLUMN_DEFINITION = "jsonb";
        public static final String PUBLISHED_AT = "published_at";
        public static final String CREATED_AT = "created_at";

        // Outbox event types
        public static final String PAYMENT_ID = "payment_id";
        public static final String BOOKING_ID = "booking_id";
        public static final String REFUND_ID = "refund_id";
        public static final String AMOUNT = "amount";
        public static final String CURRENCY = "currency";
        public static final String TIMESTAMP = "timestamp";
        public static final String REASON = "reason";
        public static final String UNKNOWN = "Unknown";
        public static final String PAYMENT_COMPLETED_EVENT = "PaymentCompleted";
        public static final String PAYMENT_FAILED_EVENT = "PaymentFailed";
        public static final String REFUND_COMPLETED_EVENT = "RefundCompleted";
    }

    public final class BkgPaymentsConstants {
        // Entity constants
        public static final String TABLE_NAME = "payments";
        public static final String INDEX_BOOKING_ID = "idx_payments_booking_id";
        public static final String INDEX_EXTERNAL_PAYMENT_ID = "idx_payments_external_payment_id";
        public static final String INDEX_USER_ID = "idx_payments_user_id";
        public static final String INDEX_STATUS = "idx_payments_status";
        public static final String ID = "id";
        public static final String BOOKING_ID = "booking_id";
        public static final String USER_ID = "user_id";
        public static final String AMOUNT = "amount";
        public static final String CURRENCY = "currency";
        public static final String STATUS = "status";
        public static final String PAYMENT_METHOD = "payment_method";
        public static final String EXTERNAL_PAYMENT_ID = "external_payment_id";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
        public static final String VERSION = "version";
        public static final String FAILURE_REASON = "failure_reason";
        public static final String FAILURE_REASON_COLUMN_DEFINITION = "TEXT";
        public static final String IDEMPOTENCY_KEY = "idempotency_key";
        public static final String RETRY_COUNT    = "retry_count";
        public static final String MAX_RETRIES    = "max_retries";
        public static final String NEXT_RETRY_AT  = "next_retry_at";
    }

}

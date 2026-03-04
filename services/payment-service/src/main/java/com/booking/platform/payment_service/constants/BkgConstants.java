package com.booking.platform.payment_service.constants;

import java.time.Instant;

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

        // Outbox event types
        public static final String PAYMENT_SUCCEEDED_EVENT = "PaymentSucceeded";
        public static final String PAYMENT_FAILED_EVENT = "PaymentFailed";
        public static final String REFUND_SUCCEEDED_EVENT = "RefundSucceeded";
        public static final String REFUND_FAILED_EVENT = "RefundFailed";
        public static final String PAYMENT_ID = "payment_id";
        public static final String BOOKING_ID = "booking_id";
        public static final String REFUND_ID = "refund_id";
        public static final String AMOUNT = "amount";
        public static final String CURRENCY = "currency";
        public static final String TIMESTAMP = "timestamp";
        public static final String REASON = "reason";
        public static final String PAYMENT_COMPLETED_EVENT_TYPE = "PaymentCompleted";
        public static final String PAYMENT_FAILED_EVENT_TYPE = "PaymentFailed";
        public static final String REFUND_COMPLETED_EVENT_TYPE = "RefundCompleted";
    }

}

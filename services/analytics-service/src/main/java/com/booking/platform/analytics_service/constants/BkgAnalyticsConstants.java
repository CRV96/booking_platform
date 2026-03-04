package com.booking.platform.analytics_service.constants;

import org.apache.kafka.common.protocol.types.Field;

public final class BkgAnalyticsConstants {
    private BkgAnalyticsConstants(){}

    public static final String PAYLOAD_PAYMENT_ID = "paymentId";
    public static final String PAYLOAD_BOOKING_ID = "bookingId";
    public static final String PAYLOAD_AMOUNT = "amount";
    public static final String PAYLOAD_CURRENCY = "currency";
    public static final String PAYLOAD_REASON = "reason";
    public static final String PAYLOAD_REFUND_ID = "refundId";

    public final class BkgPaymentConstants{
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
}

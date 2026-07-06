package com.booking.platform.booking_service.messaging.consumer;

import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private BookingService bookingService;

    @InjectMocks private PaymentEventConsumer consumer;

    private static final UUID BOOKING_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String PAYMENT_ID = "pay-1";

    // ── onPaymentCompleted ────────────────────────────────────────────────────

    @Test
    void onPaymentCompleted_validBookingId_confirmsBooking() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId(BOOKING_ID.toString())
                .setAmount(100.0)
                .setCurrency("USD")
                .build();

        consumer.onPaymentCompleted(record("payment.completed", BOOKING_ID.toString(), event));

        verify(bookingService).confirmBooking(BOOKING_ID);
    }

    @Test
    void onPaymentCompleted_invalidBookingId_throwsIllegalArgument() {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId("not-a-uuid")
                .build();

        assertThatThrownBy(() ->
                consumer.onPaymentCompleted(record("payment.completed", "not-a-uuid", event)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(bookingService);
    }

    // ── onPaymentFailed ───────────────────────────────────────────────────────

    @Test
    void onPaymentFailed_validBookingId_cancelsBookingWithReason() {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId(BOOKING_ID.toString())
                .setReason("card declined")
                .build();

        consumer.onPaymentFailed(record("payment.failed", BOOKING_ID.toString(), event));

        verify(bookingService).cancelBookingOnPaymentFailure(BOOKING_ID, "card declined");
    }

    @Test
    void onPaymentFailed_invalidBookingId_throwsIllegalArgument() {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId("bad-id")
                .setReason("reason")
                .build();

        assertThatThrownBy(() ->
                consumer.onPaymentFailed(record("payment.failed", "bad-id", event)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(bookingService);
    }

    // ── onRefundCompleted ─────────────────────────────────────────────────────

    @Test
    void onRefundCompleted_validBookingId_marksAsRefunded() {
        RefundCompletedEvent event = RefundCompletedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId(BOOKING_ID.toString())
                .setRefundId("ref-1")
                .setAmount(99.99)
                .setCurrency("USD")
                .build();

        consumer.onRefundCompleted(record("payment.refund.completed", BOOKING_ID.toString(), event));

        verify(bookingService).markBookingAsRefunded(BOOKING_ID);
    }

    @Test
    void onRefundCompleted_invalidBookingId_throwsIllegalArgument() {
        RefundCompletedEvent event = RefundCompletedEvent.newBuilder()
                .setPaymentId(PAYMENT_ID)
                .setBookingId("bad-uuid")
                .setRefundId("ref-1")
                .build();

        assertThatThrownBy(() ->
                consumer.onRefundCompleted(record("payment.refund.completed", "bad-uuid", event)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(bookingService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private <V> ConsumerRecord<String, V> record(String topic, String key, V value) {
        return new ConsumerRecord<>(topic, 0, 0L, key, value);
    }
}

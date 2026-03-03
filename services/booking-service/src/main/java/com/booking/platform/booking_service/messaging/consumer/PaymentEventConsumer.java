package com.booking.platform.booking_service.messaging.consumer;

import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for payment lifecycle events.
 *
 * <p>Handles the full payment lifecycle:
 * <ul>
 *   <li>{@code PAYMENT_COMPLETED}        → confirms the booking (PENDING → CONFIRMED) (P3-06)</li>
 *   <li>{@code PAYMENT_FAILED}           → cancels the booking, releases seats (PENDING → CANCELLED) (P3-07)</li>
 *   <li>{@code PAYMENT_REFUND_COMPLETED} → marks booking as refunded (CANCELLED → REFUNDED) (P4-05)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final BookingService bookingService;

    /**
     * Happy path: payment succeeded → confirm the booking.
     * This triggers BookingConfirmedEvent → ticket generation + confirmation email.
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            containerFactory = "paymentCompletedListenerFactory"
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record) {
        PaymentCompletedEvent event = record.value();
        log.info("[PAYMENT_COMPLETED] paymentId='{}', bookingId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(),
                event.getBookingId(),
                event.getAmount(),
                event.getCurrency(),
                record.partition(),
                record.offset());

        UUID bookingId = UUID.fromString(event.getBookingId());
        bookingService.confirmBooking(bookingId);

        log.info("Booking confirmed: bookingId='{}'", event.getBookingId());
    }

    /**
     * Compensation path (P3-07): payment failed → cancel the booking and release seats.
     * This triggers BookingCancelledEvent → cancellation email.
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            containerFactory = "paymentFailedListenerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailedEvent> record) {
        PaymentFailedEvent event = record.value();
        log.warn("[PAYMENT_FAILED] paymentId='{}', bookingId='{}', reason='{}' | partition={}, offset={}",
                event.getPaymentId(),
                event.getBookingId(),
                event.getReason(),
                record.partition(),
                record.offset());

        UUID bookingId = UUID.fromString(event.getBookingId());
        bookingService.cancelBookingOnPaymentFailure(bookingId, event.getReason());

        log.info("Booking cancelled due to payment failure: bookingId='{}'", event.getBookingId());
    }

    /**
     * Refund path (P4-05): refund completed → mark booking as REFUNDED.
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REFUND_COMPLETED,
            containerFactory = "refundCompletedListenerFactory"
    )
    public void onRefundCompleted(ConsumerRecord<String, RefundCompletedEvent> record) {
        RefundCompletedEvent event = record.value();
        log.info("[REFUND_COMPLETED] paymentId='{}', bookingId='{}', refundId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(),
                event.getBookingId(),
                event.getRefundId(),
                event.getAmount(),
                event.getCurrency(),
                record.partition(),
                record.offset());

        UUID bookingId = UUID.fromString(event.getBookingId());
        bookingService.markRefunded(bookingId);

        log.info("Booking marked as REFUNDED: bookingId='{}'", event.getBookingId());
    }
}

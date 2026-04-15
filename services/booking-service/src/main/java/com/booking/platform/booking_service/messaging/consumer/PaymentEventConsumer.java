package com.booking.platform.booking_service.messaging.consumer;

import com.booking.platform.booking_service.service.BookingService;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
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
        ApplicationLogger.logMessage(log, Level.INFO,
                "[PAYMENT_COMPLETED] paymentId='{}', bookingId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getAmount(), event.getCurrency(),
                record.partition(), record.offset());

        UUID bookingId = parseBookingId(event.getBookingId(), "PAYMENT_COMPLETED");
        bookingService.confirmBooking(bookingId);

        ApplicationLogger.logMessage(log, Level.INFO, "Booking confirmed: bookingId='{}'", event.getBookingId());
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
        ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.BOOKING_CANCELLATION_FAILED,
                "[PAYMENT_FAILED] paymentId='{}', bookingId='{}', reason='{}' | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getReason(),
                record.partition(), record.offset());

        UUID bookingId = parseBookingId(event.getBookingId(), "PAYMENT_FAILED");
        bookingService.cancelBookingOnPaymentFailure(bookingId, event.getReason());

        ApplicationLogger.logMessage(log, Level.INFO, "Booking cancelled due to payment failure: bookingId='{}'", event.getBookingId());
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
        ApplicationLogger.logMessage(log, Level.INFO,
                "[REFUND_COMPLETED] paymentId='{}', bookingId='{}', refundId='{}', amount={} {} | partition={}, offset={}",
                event.getPaymentId(), event.getBookingId(), event.getRefundId(),
                event.getAmount(), event.getCurrency(), record.partition(), record.offset());

        UUID bookingId = parseBookingId(event.getBookingId(), "REFUND_COMPLETED");
        bookingService.markBookingAsRefunded(bookingId);

        ApplicationLogger.logMessage(log, Level.INFO, "Booking marked as REFUNDED: bookingId='{}'", event.getBookingId());
    }

    private UUID parseBookingId(String bookingId, String eventType) {
        try {
            return UUID.fromString(bookingId);
        } catch (IllegalArgumentException e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.BOOKING_CREATION_FAILED,
                    "[{}] Invalid bookingId '{}', sending to DLT", eventType, bookingId);
            throw e;
        }
    }
}

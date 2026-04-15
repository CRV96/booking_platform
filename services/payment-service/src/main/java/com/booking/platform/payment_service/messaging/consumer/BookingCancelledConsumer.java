package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.event.Level;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that processes booking-cancelled events by initiating refunds (P4-05).
 *
 * <p>When a confirmed booking is cancelled, the payment needs to be refunded.
 * This consumer delegates to {@link PaymentService#processRefund(String)},
 * which handles:
 * <ul>
 *   <li>Guard checks (only COMPLETED payments are refundable)</li>
 *   <li>Gateway interaction (Stripe or mock refund)</li>
 *   <li>Status updates (COMPLETED → REFUND_INITIATED → REFUNDED)</li>
 *   <li>Outbox event writing (RefundCompleted → Kafka via poller)</li>
 * </ul>
 *
 * <p>Intentionally thin — extracts the bookingId from the Protobuf message
 * and passes it to the service layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCancelledConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CANCELLED,
            containerFactory = "bookingCancelledListenerFactory"
    )
    public void onBookingCancelled(ConsumerRecord<String, BookingCancelledEvent> record) {
        BookingCancelledEvent event = record.value();

        ApplicationLogger.logMessage(log, Level.INFO, "[BOOKING_CANCELLED] bookingId='{}', reason='{}' | partition={}, offset={}",
                event.getBookingId(),
                event.getReason(),
                record.partition(),
                record.offset());

        paymentService.processRefund(event.getBookingId());
    }
}

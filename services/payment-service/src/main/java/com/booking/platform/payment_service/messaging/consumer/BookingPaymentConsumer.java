package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kafka consumer that processes booking-created events by initiating payment.
 *
 * <p>Delegates all business logic to {@link PaymentService}, which handles:
 * <ul>
 *   <li>Idempotency (duplicate Kafka messages won't create duplicate payments)</li>
 *   <li>Gateway interaction (Stripe or mock, selected by config)</li>
 *   <li>Persistence (PaymentEntity lifecycle in PostgreSQL)</li>
 *   <li>Event publishing (PaymentCompleted / PaymentFailed to Kafka)</li>
 * </ul>
 *
 * <p>This consumer is intentionally thin — it extracts fields from the Protobuf
 * message and passes them to the service layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CREATED,
            containerFactory = "bookingCreatedListenerFactory"
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();

        log.info("[BOOKING_CREATED] bookingId='{}', eventId='{}', amount={} {} | partition={}, offset={}",
                event.getBookingId(),
                event.getEventId(),
                event.getTotalPrice(),
                event.getCurrency(),
                record.partition(),
                record.offset());

        paymentService.processPayment(
                event.getBookingId(),
                event.getUserId(),
                BigDecimal.valueOf(event.getTotalPrice()),
                event.getCurrency()
        );
    }
}

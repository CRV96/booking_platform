package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.event.Level;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p><b>Legacy auto-charge path (being strangled).</b> This consumer charges the
 * card automatically the moment a booking is created. The new checkout flow instead
 * collects the card on the checkout page and initiates payment when the user clicks
 * "Pay" (createPaymentIntent + Stripe webhook). This bean is therefore gated behind
 * {@code payment.auto-charge.on-booking-created.enabled}, which defaults to {@code true}
 * to preserve current behaviour. Set it to {@code false} to disable the auto-charge and
 * let the checkout-driven flow own payment. Once the new flow fully replaces this one,
 * this consumer can be deleted.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "payment.auto-charge.on-booking-created.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class BookingPaymentConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CREATED,
            containerFactory = "bookingCreatedListenerFactory"
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();

        ApplicationLogger.logMessage(log, Level.INFO, "[BOOKING_CREATED] bookingId='{}', eventId='{}', amount={} {} | partition={}, offset={}",
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

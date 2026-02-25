package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.google.protobuf.MessageLite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer for booking events — stub payment processor.
 *
 * <p>This is a temporary stub that auto-succeeds all payments. When a
 * {@link BookingCreatedEvent} arrives, it immediately publishes a
 * {@link PaymentCompletedEvent} without any actual payment processing.
 *
 * <p>In production, this would:
 * <ol>
 *   <li>Call Stripe / payment gateway to create a charge</li>
 *   <li>Wait for webhook confirmation</li>
 *   <li>Publish {@code PaymentCompletedEvent} on success or {@code PaymentFailedEvent} on failure</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPaymentConsumer {

    private final KafkaTemplate<String, MessageLite> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopics.BOOKING_CREATED,
            containerFactory = "bookingCreatedListenerFactory"
    )
    public void onBookingCreated(ConsumerRecord<String, BookingCreatedEvent> record) {
        BookingCreatedEvent event = record.value();
        log.info("[BOOKING_CREATED] bookingId='{}', eventId='{}', userId='{}', total={} {} | partition={}, offset={}",
                event.getBookingId(),
                event.getEventId(),
                event.getUserId(),
                event.getTotalPrice(),
                event.getCurrency(),
                record.partition(),
                record.offset());

        // ── STUB: auto-success — skip real payment processing ─────────────────
        PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.newBuilder()
                .setPaymentId("pay-" + UUID.randomUUID().toString().substring(0, 8))
                .setBookingId(event.getBookingId())
                .setAmount(event.getTotalPrice())
                .setCurrency(event.getCurrency())
                .setTimestamp(Instant.now().toString())
                .build();

        CompletableFuture<SendResult<String, MessageLite>> future =
                kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, event.getBookingId(), paymentEvent);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish PaymentCompletedEvent for booking '{}': {}",
                        event.getBookingId(), ex.getMessage());
            } else {
                log.info("[STUB] Payment auto-approved for booking '{}' → published PaymentCompletedEvent (partition={}, offset={})",
                        event.getBookingId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}

package com.booking.platform.payment_service.messaging.consumer;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kafka consumer that processes booking-created events by simulating payment.
 *
 * <p>This is a <b>stub</b> implementation for P3-06/P3-07. In production,
 * this would call Stripe or another payment gateway.
 *
 * <p><b>Failure simulation (P3-07)</b>: the {@code payment.stub.failure-rate}
 * property (0–100) controls the percentage of payments that fail. Default is 0
 * (all succeed). Set to 100 to simulate all failures.
 *
 * <p>On success → publishes {@link PaymentCompletedEvent} → booking-service confirms.
 * <br>On failure → publishes {@link PaymentFailedEvent} → booking-service cancels + releases seats.
 */
@Slf4j
@Component
public class BookingPaymentConsumer {

    private final KafkaTemplate<String, MessageLite> kafkaTemplate;
    private final int failureRate;

    public BookingPaymentConsumer(
            KafkaTemplate<String, MessageLite> kafkaTemplate,
            @Value("${payment.stub.failure-rate:0}") int failureRate) {
        this.kafkaTemplate = kafkaTemplate;
        this.failureRate = failureRate;
    }

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

        String paymentId = UUID.randomUUID().toString();

        // P3-07: Simulate payment failure based on configured rate
        if (shouldSimulateFailure()) {
            log.warn("Payment FAILED (stub simulation): bookingId='{}', paymentId='{}'",
                    event.getBookingId(), paymentId);

            PaymentFailedEvent failedEvent = PaymentFailedEvent.newBuilder()
                    .setPaymentId(paymentId)
                    .setBookingId(event.getBookingId())
                    .setReason("Card declined (stub simulation)")
                    .setTimestamp(Instant.now().toString())
                    .build();

            send(KafkaTopics.PAYMENT_FAILED, event.getBookingId(), failedEvent);
        } else {
            log.info("Payment APPROVED (stub): bookingId='{}', paymentId='{}'",
                    event.getBookingId(), paymentId);

            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.newBuilder()
                    .setPaymentId(paymentId)
                    .setBookingId(event.getBookingId())
                    .setAmount(event.getTotalPrice())
                    .setCurrency(event.getCurrency())
                    .setTimestamp(Instant.now().toString())
                    .build();

            send(KafkaTopics.PAYMENT_COMPLETED, event.getBookingId(), completedEvent);
        }
    }

    private boolean shouldSimulateFailure() {
        if (failureRate <= 0) return false;
        if (failureRate >= 100) return true;
        return ThreadLocalRandom.current().nextInt(100) < failureRate;
    }

    private void send(String topic, String key, MessageLite message) {
        CompletableFuture<SendResult<String, MessageLite>> future =
                kafkaTemplate.send(topic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic '{}' for key '{}': {}",
                        topic, key, ex.getMessage());
            } else {
                log.debug("Published to topic='{}', partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}

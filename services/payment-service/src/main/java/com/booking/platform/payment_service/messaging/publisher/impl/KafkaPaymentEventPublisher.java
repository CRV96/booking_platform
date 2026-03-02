package com.booking.platform.payment_service.messaging.publisher.impl;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.messaging.publisher.PaymentEventPublisher;
import com.google.protobuf.MessageLite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka-backed implementation of {@link PaymentEventPublisher}.
 *
 * <p>Builds Protobuf messages from {@link PaymentEntity} and publishes them
 * asynchronously. The booking ID is used as the message key to guarantee
 * ordering per booking across partitions.
 *
 * <p>Follows the same fire-and-forget pattern as
 * {@code KafkaBookingEventPublisher} in booking-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, MessageLite> kafkaTemplate;

    @Override
    public void publishPaymentCompleted(PaymentEntity payment) {
        PaymentCompletedEvent message = PaymentCompletedEvent.newBuilder()
                .setPaymentId(payment.getId().toString())
                .setBookingId(payment.getBookingId())
                .setAmount(payment.getAmount().doubleValue())
                .setCurrency(payment.getCurrency())
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.PAYMENT_COMPLETED, payment.getBookingId(), message);
    }

    @Override
    public void publishPaymentFailed(PaymentEntity payment) {
        PaymentFailedEvent message = PaymentFailedEvent.newBuilder()
                .setPaymentId(payment.getId().toString())
                .setBookingId(payment.getBookingId())
                .setReason(payment.getFailureReason() != null ? payment.getFailureReason() : "Unknown")
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.PAYMENT_FAILED, payment.getBookingId(), message);
    }

    // ── Private helper ────────────────────────────────────────────────────────

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

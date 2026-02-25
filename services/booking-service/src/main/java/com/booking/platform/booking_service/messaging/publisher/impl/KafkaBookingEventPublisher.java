package com.booking.platform.booking_service.messaging.publisher.impl;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.messaging.publisher.BookingEventPublisher;
import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.google.protobuf.MessageLite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka-backed implementation of {@link BookingEventPublisher}.
 *
 * <p>Each method builds the appropriate Protobuf message from the {@link BookingEntity},
 * then publishes it asynchronously using {@link KafkaTemplate}. The booking ID is used
 * as the message key to guarantee ordering per booking across partitions.
 *
 * <p>Publishing is fire-and-forget from the service layer's perspective — the send
 * is non-blocking and failures are logged but never propagate to the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaBookingEventPublisher implements BookingEventPublisher {

    private final KafkaTemplate<String, MessageLite> kafkaTemplate;

    @Override
    public void publishBookingCreated(BookingEntity booking) {
        BookingCreatedEvent message = BookingCreatedEvent.newBuilder()
                .setBookingId(booking.getId().toString())
                .setEventId(booking.getEventId())
                .setUserId(booking.getUserId())
                .setSeatCategory(booking.getSeatCategory())
                .setQuantity(booking.getQuantity())
                .setTotalPrice(booking.getTotalPrice().doubleValue())
                .setCurrency(booking.getCurrency())
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.BOOKING_CREATED, booking.getId().toString(), message);
    }

    @Override
    public void publishBookingConfirmed(BookingEntity booking) {
        BookingConfirmedEvent message = BookingConfirmedEvent.newBuilder()
                .setBookingId(booking.getId().toString())
                .setEventId(booking.getEventId())
                .setUserId(booking.getUserId())
                .setTimestamp(Instant.now().toString())
                .setSeatCategory(booking.getSeatCategory())
                .setQuantity(booking.getQuantity())
                .setTotalPrice(booking.getTotalPrice().doubleValue())
                .setCurrency(booking.getCurrency())
                .setEventTitle(booking.getEventTitle())
                .build();

        send(KafkaTopics.BOOKING_CONFIRMED, booking.getId().toString(), message);
    }

    @Override
    public void publishBookingCancelled(BookingEntity booking) {
        BookingCancelledEvent message = BookingCancelledEvent.newBuilder()
                .setBookingId(booking.getId().toString())
                .setEventId(booking.getEventId())
                .setUserId(booking.getUserId())
                .setReason(booking.getCancellationReason() != null ? booking.getCancellationReason() : "")
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.BOOKING_CANCELLED, booking.getId().toString(), message);
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

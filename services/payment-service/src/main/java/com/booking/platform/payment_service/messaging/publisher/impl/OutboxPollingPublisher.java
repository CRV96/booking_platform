package com.booking.platform.payment_service.messaging.publisher.impl;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MessageLite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox polling publisher — the "relay" side of the Transactional Outbox Pattern (P4-04).
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Every 500ms, reads all rows from {@code outbox_events} where {@code published_at IS NULL}</li>
 *   <li>For each event: parses the JSON payload → builds a Protobuf message → sends to Kafka</li>
 *   <li>On successful Kafka send: sets {@code published_at = NOW()} (marks as published)</li>
 *   <li>On Kafka failure: stops processing and retries on the next poll cycle (maintains ordering)</li>
 * </ol>
 *
 * <p><b>Cleanup:</b> Every hour, deletes events that were published more than 24 hours ago.
 * Published events are kept for a 24-hour window to allow debugging/auditing.
 *
 * <p><b>Ordering guarantee:</b> Events are read in {@code created_at} order (FIFO).
 * If publishing one event fails, we stop and retry on the next cycle — this prevents
 * out-of-order delivery within an aggregate (e.g. PaymentCompleted arriving before PaymentFailed).
 *
 * <p><b>At-least-once delivery:</b> If the poller successfully sends to Kafka but crashes
 * before marking {@code published_at}, the event will be re-published on restart.
 * Consumers must be idempotent (they already are — booking-service checks booking status
 * before confirming/cancelling).
 *
 * <p><b>Why synchronous send (.get())?</b> We use {@code kafkaTemplate.send().get()} instead
 * of fire-and-forget because we need confirmation that Kafka accepted the message before
 * marking it as published. Fire-and-forget could lose events if Kafka rejects the message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollingPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, MessageLite> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ── Poll: read unpublished → publish to Kafka → mark published ──────────

    /**
     * Polls for unpublished outbox events every 500ms and publishes them to Kafka.
     *
     * <p>Each event is published synchronously ({@code .get()}) to ensure Kafka
     * confirms receipt before we mark it as published in the database.
     */
    @Scheduled(fixedRateString = "${outbox.poll.interval:500}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventEntity> events =
                outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        if (events.isEmpty()) {
            return;
        }

        log.debug("Outbox poller found {} unpublished event(s)", events.size());

        for (OutboxEventEntity event : events) {
            try {
                MessageLite protoMessage = buildProtoMessage(event);
                String topic = resolveTopicFromEventType(event.getEventType());
                String key = extractBookingIdFromPayload(event);

                // Synchronous send — blocks until Kafka confirms receipt
                kafkaTemplate.send(topic, key, protoMessage).get();

                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                log.info("Outbox event published: id='{}', type='{}', topic='{}', key='{}'",
                        event.getId(), event.getEventType(), topic, key);

            } catch (Exception e) {
                // Stop processing to maintain event ordering.
                // This event (and all after it) will be retried on the next poll cycle.
                log.warn("Failed to publish outbox event id='{}', type='{}', will retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                break;
            }
        }
    }

    // ── Cleanup: delete published events older than 24h ─────────────────────

    /**
     * Cleans up published outbox events older than 24 hours.
     * Runs every hour. Published events are kept for 24h to allow debugging/auditing.
     */
    @Scheduled(fixedRateString = "${outbox.cleanup.interval:3600000}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        outboxEventRepository.deleteByPublishedAtBefore(cutoff);
        log.debug("Outbox cleanup: deleted events published before {}", cutoff);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Maps event_type to Kafka topic.
     *
     * @throws IllegalArgumentException if the event type is unknown
     */
    private String resolveTopicFromEventType(String eventType) {
        return switch (eventType) {
            case "PaymentCompleted" -> KafkaTopics.PAYMENT_COMPLETED;
            case "PaymentFailed" -> KafkaTopics.PAYMENT_FAILED;
            case "RefundCompleted" -> KafkaTopics.PAYMENT_REFUND_COMPLETED;
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        };
    }

    /**
     * Builds the Protobuf message from the JSON payload stored in the outbox event.
     * The JSON fields mirror the Protobuf message fields exactly.
     */
    private MessageLite buildProtoMessage(OutboxEventEntity event) {
        try {
            JsonNode json = objectMapper.readTree(event.getPayload());

            return switch (event.getEventType()) {
                case "PaymentCompleted" -> PaymentCompletedEvent.newBuilder()
                        .setPaymentId(json.get("payment_id").asText())
                        .setBookingId(json.get("booking_id").asText())
                        .setAmount(json.get("amount").asDouble())
                        .setCurrency(json.get("currency").asText())
                        .setTimestamp(json.get("timestamp").asText())
                        .build();

                case "PaymentFailed" -> PaymentFailedEvent.newBuilder()
                        .setPaymentId(json.get("payment_id").asText())
                        .setBookingId(json.get("booking_id").asText())
                        .setReason(json.get("reason").asText())
                        .setTimestamp(json.get("timestamp").asText())
                        .build();
//TODO: refactor this strings, use constants instead, a lot of duplicates
                case "RefundCompleted" -> RefundCompletedEvent.newBuilder()
                        .setPaymentId(json.get("payment_id").asText())
                        .setBookingId(json.get("booking_id").asText())
                        .setRefundId(json.get("refund_id").asText())
                        .setAmount(json.get("amount").asDouble())
                        .setCurrency(json.get("currency").asText())
                        .setTimestamp(json.get("timestamp").asText())
                        .build();

                default -> throw new IllegalArgumentException(
                        "Cannot build proto for unknown event type: " + event.getEventType());
            };
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse outbox event payload: id=" + event.getId(), e);
        }
    }

    /**
     * Extracts the booking_id from the JSON payload to use as the Kafka message key.
     * This ensures ordering per booking across Kafka partitions.
     */
    private String extractBookingIdFromPayload(OutboxEventEntity event) {
        try {
            JsonNode json = objectMapper.readTree(event.getPayload());
            return json.get("booking_id").asText();
        } catch (Exception e) {
            log.warn("Could not extract booking_id from outbox event id='{}', using aggregate_id",
                    event.getId());
            return event.getAggregateId();
        }
    }
}

package com.booking.platform.payment_service.messaging.publisher;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.PaymentCompletedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
import com.booking.platform.common.events.RefundCompletedEvent;
import com.booking.platform.payment_service.constants.BkgConstants.BkgOutboxConstants;
import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MessageLite;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox polling publisher — the "relay" side of the Transactional Outbox Pattern.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Every 500ms, reads up to {@code outbox.poll.batch-size} rows from {@code outbox_events}
 *       where {@code published_at IS NULL}</li>
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

    @Value("${outbox.poll.batch-size:100}")
    private int batchSize;

    @Value("${outbox.cleanup.retention-hours:24}")
    private int retentionHours;

    // ── Poll: read unpublished → publish to Kafka → mark published ──────────

    /**
     * Polls for unpublished outbox events every 500ms and publishes them to Kafka.
     *
     * <p>Uses {@code fixedDelay} (not {@code fixedRate}) so the next poll starts
     * 500ms after the previous one finishes — preventing overlapping polls under
     * backlog. Reads at most {@code outbox.poll.batch-size} events per tick.
     * Events are published synchronously ({@code .get()}) to ensure Kafka confirms
     * receipt before marking as published.
     */
    @Scheduled(fixedDelayString = "${outbox.poll.interval:500}")
    public void pollAndPublish() {
        List<OutboxEventEntity> events =
                outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
                        PageRequest.of(0, batchSize));

        if (events.isEmpty()) {
            return;
        }

        ApplicationLogger.logMessage(log, Level.DEBUG, "Outbox poller found {} unpublished event(s)", events.size());

        for (OutboxEventEntity event : events) {
            try {
                final JsonNode payload = objectMapper.readTree(event.getPayload());
                final MessageLite protoMessage = buildProtoMessage(event, payload);
                final String topic = resolveTopicFromEventType(event.getEventType());
                final String key = payload.path(BkgOutboxConstants.BOOKING_ID).asText(event.getAggregateId());

                // Synchronous send — blocks until Kafka confirms receipt
                kafkaTemplate.send(topic, key, protoMessage).get();

                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                ApplicationLogger.logMessage(log, Level.INFO, "Outbox event published: id='{}', type='{}', topic='{}', key='{}'",
                        event.getId(), event.getEventType(), topic, key);

            } catch (Exception e) {
                // Stop processing to maintain event ordering.
                // This event (and all after it) will be retried on the next poll cycle.
                ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.OUTBOX_PUBLISH_FAILED, "Failed to publish outbox event id='{}', type='{}', will retry: {}",
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
        final Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));
        outboxEventRepository.deleteByPublishedAtBefore(cutoff);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Outbox cleanup: deleted events published before {}", cutoff);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Maps event_type to Kafka topic.
     *
     * @throws IllegalArgumentException if the event type is unknown
     */
    private String resolveTopicFromEventType(String eventType) {
        return switch (eventType) {
            case BkgOutboxConstants.PAYMENT_COMPLETED_EVENT -> KafkaTopics.PAYMENT_COMPLETED;
            case BkgOutboxConstants.PAYMENT_FAILED_EVENT -> KafkaTopics.PAYMENT_FAILED;
            case BkgOutboxConstants.REFUND_COMPLETED_EVENT -> KafkaTopics.PAYMENT_REFUND_COMPLETED;
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        };
    }

    /**
     * Builds the Protobuf message from the pre-parsed JSON payload.
     * Uses {@code path(field)} instead of {@code get(field)} so missing fields return
     * an empty {@code MissingNode} rather than {@code null}, avoiding NPEs.
     */
    private MessageLite buildProtoMessage(OutboxEventEntity event, JsonNode json) {
        try {
            if (!json.has(BkgOutboxConstants.BOOKING_ID)) {
                throw new IllegalArgumentException("Invalid payload: missing booking_id");
            }

            return switch (event.getEventType()) {
                case BkgOutboxConstants.PAYMENT_COMPLETED_EVENT -> PaymentCompletedEvent.newBuilder()
                        .setPaymentId(json.path(BkgOutboxConstants.PAYMENT_ID).asText())
                        .setBookingId(json.path(BkgOutboxConstants.BOOKING_ID).asText())
                        .setAmount(json.path(BkgOutboxConstants.AMOUNT).asDouble())
                        .setCurrency(json.path(BkgOutboxConstants.CURRENCY).asText())
                        .setTimestamp(json.path(BkgOutboxConstants.TIMESTAMP).asText())
                        .build();

                case BkgOutboxConstants.PAYMENT_FAILED_EVENT -> PaymentFailedEvent.newBuilder()
                        .setPaymentId(json.path(BkgOutboxConstants.PAYMENT_ID).asText())
                        .setBookingId(json.path(BkgOutboxConstants.BOOKING_ID).asText())
                        .setReason(json.path(BkgOutboxConstants.REASON).asText())
                        .setTimestamp(json.path(BkgOutboxConstants.TIMESTAMP).asText())
                        .build();

                case BkgOutboxConstants.REFUND_COMPLETED_EVENT -> RefundCompletedEvent.newBuilder()
                        .setPaymentId(json.path(BkgOutboxConstants.PAYMENT_ID).asText())
                        .setBookingId(json.path(BkgOutboxConstants.BOOKING_ID).asText())
                        .setRefundId(json.path(BkgOutboxConstants.REFUND_ID).asText())
                        .setAmount(json.path(BkgOutboxConstants.AMOUNT).asDouble())
                        .setCurrency(json.path(BkgOutboxConstants.CURRENCY).asText())
                        .setTimestamp(json.path(BkgOutboxConstants.TIMESTAMP).asText())
                        .build();

                default -> throw new IllegalArgumentException(
                        "Cannot build proto for unknown event type: " + event.getEventType());
            };
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse outbox event payload: id=" + event.getId(), e);
        }
    }

}

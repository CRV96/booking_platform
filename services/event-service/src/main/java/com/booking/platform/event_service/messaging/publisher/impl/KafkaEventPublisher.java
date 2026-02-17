package com.booking.platform.event_service.messaging.publisher.impl;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.common.events.VenueSnapshot;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.messaging.publisher.EventPublisher;
import com.google.protobuf.MessageLite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes domain events to Kafka when the event lifecycle changes.
 *
 * <p>Each method:
 * <ol>
 *   <li>Builds the appropriate Protobuf message from the {@link EventDocument}</li>
 *   <li>Calls {@code kafkaTemplate.send(topic, key, message)} — non-blocking</li>
 *   <li>Attaches a callback that logs success (offset) or failure (exception)</li>
 * </ol>
 *
 * <p>Publishing is <b>fire-and-forget from the service layer's perspective</b>:
 * the {@link org.springframework.kafka.core.KafkaTemplate} send is asynchronous
 * and never blocks {@link com.booking.platform.event_service.service.EventService}.
 * If the broker is temporarily unavailable the producer will retry (configured via
 * {@code spring.kafka.producer.retries}) and buffer messages in its internal batch.
 *
 * <p>Message keys are always the {@code eventId} so that all events for the same
 * entity land on the same partition — this guarantees ordering per entity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, MessageLite> kafkaTemplate;

    // ── Public publish methods ────────────────────────────────────────────────

    /**
     * Published after a new event is persisted in DRAFT status.
     * Topic: {@value KafkaTopics#EVENT_CREATED}
     */
    @Override
    public void publishEventCreated(EventDocument event) {
        EventCreatedEvent message = EventCreatedEvent.newBuilder()
                .setEventId(event.getId())
                .setTitle(event.getTitle())
                .setCategory(event.getCategory().name())
                .setVenue(toVenueSnapshot(event))
                .setDateTime(event.getDateTime().toString())
                .setOrganizerId(event.getOrganizer().getUserId())
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.EVENT_CREATED, event.getId(), message);
    }

    /**
     * Published after an event's fields are modified.
     * Topic: {@value KafkaTopics#EVENT_UPDATED}
     *
     * @param changedFields names of the fields that were updated (e.g. ["title","dateTime"])
     */
    @Override
    public void publishEventUpdated(EventDocument event, List<String> changedFields) {
        EventUpdatedEvent message = EventUpdatedEvent.newBuilder()
                .setEventId(event.getId())
                .addAllChangedFields(changedFields)
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.EVENT_UPDATED, event.getId(), message);
    }

    /**
     * Published after an event transitions DRAFT → PUBLISHED.
     * Topic: {@value KafkaTopics#EVENT_PUBLISHED}
     */
    @Override
    public void publishEventPublished(EventDocument event) {
        EventPublishedEvent message = EventPublishedEvent.newBuilder()
                .setEventId(event.getId())
                .setTitle(event.getTitle())
                .setCategory(event.getCategory().name())
                .setDateTime(event.getDateTime().toString())
                .setOrganizerId(event.getOrganizer().getUserId())
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.EVENT_PUBLISHED, event.getId(), message);
    }

    /**
     * Published after an event is cancelled.
     * Topic: {@value KafkaTopics#EVENT_CANCELLED}
     */
    @Override
    public void publishEventCancelled(EventDocument event, String reason) {
        EventCancelledEvent message = EventCancelledEvent.newBuilder()
                .setEventId(event.getId())
                .setReason(reason != null ? reason : "")
                .setTimestamp(Instant.now().toString())
                .build();

        send(KafkaTopics.EVENT_CANCELLED, event.getId(), message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a message to Kafka and attaches a callback for logging.
     * The send itself is non-blocking — the caller returns immediately.
     */
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

    private VenueSnapshot toVenueSnapshot(EventDocument event) {
        if (event.getVenue() == null) {
            return VenueSnapshot.getDefaultInstance();
        }
        return VenueSnapshot.newBuilder()
                .setName(event.getVenue().getName() != null ? event.getVenue().getName() : "")
                .setCity(event.getVenue().getCity() != null ? event.getVenue().getCity() : "")
                .setCountry(event.getVenue().getCountry() != null ? event.getVenue().getCountry() : "")
                .build();
    }
}

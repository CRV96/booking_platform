package com.booking.platform.event_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.event_service.service.EventVectorIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the semantic-search vector store in sync by treating event lifecycle messages
 * as "reindex this event" triggers. Runs only when semantic search is enabled.
 *
 * <p>Created/updated/published → re-embed the current state; cancelled → remove.
 * The actual embedding + store call lives in {@link EventVectorIndexer}; if it fails,
 * the shared error handler retries 3× and then routes to the DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class VectorIndexConsumer {

    private final EventVectorIndexer indexer;

    @KafkaListener(topics = KafkaTopics.EVENT_CREATED, containerFactory = "eventCreatedListenerFactory")
    public void onEventCreated(ConsumerRecord<String, EventCreatedEvent> record) {
        logReceived(KafkaTopics.EVENT_CREATED, record.value().getEventId(), record);
        indexer.index(record.value().getEventId());
    }

    @KafkaListener(topics = KafkaTopics.EVENT_UPDATED, containerFactory = "eventUpdatedListenerFactory")
    public void onEventUpdated(ConsumerRecord<String, EventUpdatedEvent> record) {
        logReceived(KafkaTopics.EVENT_UPDATED, record.value().getEventId(), record);
        indexer.index(record.value().getEventId());
    }

    @KafkaListener(topics = KafkaTopics.EVENT_PUBLISHED, containerFactory = "eventPublishedListenerFactory")
    public void onEventPublished(ConsumerRecord<String, EventPublishedEvent> record) {
        logReceived(KafkaTopics.EVENT_PUBLISHED, record.value().getEventId(), record);
        indexer.index(record.value().getEventId());
    }

    @KafkaListener(topics = KafkaTopics.EVENT_CANCELLED, containerFactory = "eventCancelledListenerFactory")
    public void onEventCancelled(ConsumerRecord<String, EventCancelledEvent> record) {
        logReceived(KafkaTopics.EVENT_CANCELLED, record.value().getEventId(), record);
        indexer.remove(record.value().getEventId());
    }

    private void logReceived(String topic, String eventId, ConsumerRecord<String, ?> record) {
        log.debug("[{}] reindex trigger eventId='{}' | partition={}, offset={}",
                topic, eventId, record.partition(), record.offset());
    }
}

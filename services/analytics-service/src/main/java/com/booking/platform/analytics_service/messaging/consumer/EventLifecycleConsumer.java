package com.booking.platform.analytics_service.messaging.consumer;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import com.booking.platform.analytics_service.dto.EventDto;
import com.booking.platform.analytics_service.service.EventAnalyticsProcessor;
import com.booking.platform.common.events.*;
import com.booking.platform.common.events.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for event lifecycle topics.
 *
 * <p>Handles: EventCreated, EventUpdated, EventPublished, EventCancelled.
 * Each listener extracts fields from the proto message and delegates
 * to {@link EventAnalyticsProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLifecycleConsumer {

    private final EventAnalyticsProcessor processor;

    @KafkaListener(
            topics = KafkaTopics.EVENT_CREATED,
            containerFactory = BkgAnalyticsConstants.BkgEventConstants.EVENT_CREATED_FACTORY
    )
    public void onEventCreated(ConsumerRecord<String, EventCreatedEvent> record) {
        EventCreatedEvent event = record.value();

        log.info("[EVENT_CREATED] eventId='{}', title='{}', category='{}' | partition={}, offset={}",
                event.getEventId(), event.getTitle(), event.getCategory(),
                record.partition(), record.offset());

        processor.processEventCreated(
                EventDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .eventId(event.getEventId())
                        .title(event.getTitle())
                        .category(event.getCategory())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.EVENT_UPDATED,
            containerFactory = BkgAnalyticsConstants.BkgEventConstants.EVENT_UPDATED_FACTORY
    )
    public void onEventUpdated(ConsumerRecord<String, EventUpdatedEvent> record) {
        EventUpdatedEvent event = record.value();

        log.info("[EVENT_UPDATED] eventId='{}', changedFields={} | partition={}, offset={}",
                event.getEventId(), event.getChangedFieldsList(),
                record.partition(), record.offset());

        processor.processEventUpdated(
                EventDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .eventId(event.getEventId())
                        .changedFields(event.getChangedFieldsList())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.EVENT_PUBLISHED,
            containerFactory = BkgAnalyticsConstants.BkgEventConstants.EVENT_PUBLISHED_FACTORY
    )
    public void onEventPublished(ConsumerRecord<String, EventPublishedEvent> record) {
        EventPublishedEvent event = record.value();

        log.info("[EVENT_PUBLISHED] eventId='{}', title='{}', category='{}' | partition={}, offset={}",
                event.getEventId(), event.getTitle(), event.getCategory(),
                record.partition(), record.offset());

        processor.processEventPublished(
                EventDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .eventId(event.getEventId())
                        .title(event.getTitle())
                        .category(event.getCategory())
                        .build());
    }

    @KafkaListener(
            topics = KafkaTopics.EVENT_CANCELLED,
            containerFactory = BkgAnalyticsConstants.BkgEventConstants.EVENT_CANCELLED_FACTORY
    )
    public void onEventCancelled(ConsumerRecord<String, EventCancelledEvent> record) {
        EventCancelledEvent event = record.value();

        log.info("[EVENT_CANCELLED] eventId='{}', reason='{}' | partition={}, offset={}",
                event.getEventId(), event.getReason(),
                record.partition(), record.offset());

        processor.processEventCancelled(
                EventDto.builder()
                        .topic(record.topic())
                        .key(record.key())
                        .eventId(event.getEventId())
                        .reason(event.getReason())
                        .build());
    }
}

package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for event-domain lifecycle messages.
 *
 * <p>Each method listens to a single topic and logs the incoming event.
 * Full email sending will be wired in P2-05 (Email Templates and Sending).
 *
 * <p>Design notes:
 * <ul>
 *   <li>Each {@code @KafkaListener} references its own {@code containerFactory} bean,
 *       which holds the correct {@link com.booking.platform.notification_service.messaging.deserializer.ProtobufDeserializer}
 *       parser for that message type.</li>
 *   <li>{@link ConsumerRecord} gives access to topic, partition, offset, and key
 *       for structured logging and debugging.</li>
 *   <li>All methods are void — Spring Kafka handles offset commit automatically
 *       after successful method return (at-least-once delivery semantics).</li>
 * </ul>
 */
@Slf4j
@Component
public class EventNotificationConsumer {

    /**
     * Receives notification when a new event is created (status: DRAFT).
     * Future: notify organizer that draft was created.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_CREATED,
            containerFactory = "eventCreatedListenerFactory"
    )
    public void onEventCreated(ConsumerRecord<String, EventCreatedEvent> record) {
        EventCreatedEvent event = record.value();
        log.info("[EVENT_CREATED] eventId='{}', title='{}', category='{}', organizer='{}' | partition={}, offset={}",
                event.getEventId(),
                event.getTitle(),
                event.getCategory(),
                event.getOrganizerId(),
                record.partition(),
                record.offset());
        // TODO P2-05: notify organizer that their event draft was created
    }

    /**
     * Receives notification when an event's fields are modified.
     * Future: notify attendees if date/venue changed on a PUBLISHED event.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_UPDATED,
            containerFactory = "eventUpdatedListenerFactory"
    )
    public void onEventUpdated(ConsumerRecord<String, EventUpdatedEvent> record) {
        EventUpdatedEvent event = record.value();
        log.info("[EVENT_UPDATED] eventId='{}', changedFields={} | partition={}, offset={}",
                event.getEventId(),
                event.getChangedFieldsList(),
                record.partition(),
                record.offset());
        // TODO P2-05: if dateTime or venue changed, notify confirmed attendees
    }

    /**
     * Receives notification when an event goes live (DRAFT → PUBLISHED).
     * Future: notify all users who saved/followed the event.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_PUBLISHED,
            containerFactory = "eventPublishedListenerFactory"
    )
    public void onEventPublished(ConsumerRecord<String, EventPublishedEvent> record) {
        EventPublishedEvent event = record.value();
        log.info("[EVENT_PUBLISHED] eventId='{}', title='{}', category='{}', dateTime='{}' | partition={}, offset={}",
                event.getEventId(),
                event.getTitle(),
                event.getCategory(),
                event.getDateTime(),
                record.partition(),
                record.offset());
        // TODO P2-05: send "Event is now live" email to followers
    }

    /**
     * Receives notification when an event is cancelled.
     * Future: notify all customers who booked tickets for this event.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_CANCELLED,
            containerFactory = "eventCancelledListenerFactory"
    )
    public void onEventCancelled(ConsumerRecord<String, EventCancelledEvent> record) {
        EventCancelledEvent event = record.value();
        log.info("[EVENT_CANCELLED] eventId='{}', reason='{}' | partition={}, offset={}",
                event.getEventId(),
                event.getReason(),
                record.partition(),
                record.offset());
        // TODO P2-05: send cancellation + refund email to all booking holders
    }
}

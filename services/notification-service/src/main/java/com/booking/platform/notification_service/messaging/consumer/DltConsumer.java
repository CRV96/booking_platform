package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Dead Letter Topic (DLT) consumer for notification-service.
 *
 * <p>Every main topic has a corresponding DLT named {@code <topic>-dlt}.
 * When a message fails all retry attempts (3 retries with exponential backoff),
 * the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
 * configured in {@link com.booking.platform.notification_service.messaging.config.KafkaConsumerConfig}
 * forwards it here.
 *
 * <p>Each listener attempts to deserialize the raw {@code byte[]} back into its
 * Protobuf type so the business context (user ID, event ID, email that failed to
 * send) is visible in the logs. If deserialization fails (poison pill — bytes that
 * were never valid Protobuf), it falls back to logging raw byte length only.
 *
 * <p>Engineers can use the logged context to:
 * <ul>
 *   <li>Know exactly which user's email was not delivered</li>
 *   <li>Identify the root cause from the DLT headers (exception + stack trace)</li>
 *   <li>Replay the message by resetting the consumer group offset on the original topic</li>
 * </ul>
 */
@Slf4j
@Component
public class DltConsumer {

    // ── Event DLTs ────────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.EVENT_CREATED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onEventCreatedDlt(ConsumerRecord<String, byte[]> record) {
        try {
            EventCreatedEvent event = EventCreatedEvent.parseFrom(record.value());
            log.error("[DLT] [EVENT_CREATED] email NOT sent | " +
                      "eventId='{}', title='{}', organizerId='{}', timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getEventId(), event.getTitle(), event.getOrganizerId(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    @KafkaListener(topics = KafkaTopics.EVENT_UPDATED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onEventUpdatedDlt(ConsumerRecord<String, byte[]> record) {
        try {
            EventUpdatedEvent event = EventUpdatedEvent.parseFrom(record.value());
            log.error("[DLT] [EVENT_UPDATED] notification NOT sent | " +
                      "eventId='{}', changedFields={}, timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getEventId(), event.getChangedFieldsList(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    @KafkaListener(topics = KafkaTopics.EVENT_PUBLISHED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onEventPublishedDlt(ConsumerRecord<String, byte[]> record) {
        try {
            EventPublishedEvent event = EventPublishedEvent.parseFrom(record.value());
            log.error("[DLT] [EVENT_PUBLISHED] notification NOT sent | " +
                      "eventId='{}', title='{}', organizerId='{}', timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getEventId(), event.getTitle(), event.getOrganizerId(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    @KafkaListener(topics = KafkaTopics.EVENT_CANCELLED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onEventCancelledDlt(ConsumerRecord<String, byte[]> record) {
        try {
            EventCancelledEvent event = EventCancelledEvent.parseFrom(record.value());
            log.error("[DLT] [EVENT_CANCELLED] cancellation email NOT sent to attendees | " +
                      "eventId='{}', reason='{}', timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getEventId(), event.getReason(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    // ── Booking DLTs ──────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.BOOKING_CREATED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onBookingCreatedDlt(ConsumerRecord<String, byte[]> record) {
        try {
            BookingCreatedEvent event = BookingCreatedEvent.parseFrom(record.value());
            log.error("[DLT] [BOOKING_CREATED] notification NOT sent | " +
                      "bookingId='{}', userId='{}', eventId='{}', timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getBookingId(), event.getUserId(), event.getEventId(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_CONFIRMED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onBookingConfirmedDlt(ConsumerRecord<String, byte[]> record) {
        try {
            BookingConfirmedEvent event = BookingConfirmedEvent.parseFrom(record.value());
            log.error("[DLT] [BOOKING_CONFIRMED] confirmation email NOT sent | " +
                      "bookingId='{}', userId='{}', eventId='{}', ticketCount={}, timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getBookingId(), event.getUserId(), event.getEventId(),
                    event.getTicketIdsList().size(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_CANCELLED + "-dlt", groupId = "notification-service-dlt-group", containerFactory = "dltListenerFactory")
    public void onBookingCancelledDlt(ConsumerRecord<String, byte[]> record) {
        try {
            BookingCancelledEvent event = BookingCancelledEvent.parseFrom(record.value());
            log.error("[DLT] [BOOKING_CANCELLED] cancellation email NOT sent | " +
                      "bookingId='{}', userId='{}', eventId='{}', reason='{}', timestamp='{}' | " +
                      "topic='{}', partition={}, offset={}",
                    event.getBookingId(), event.getUserId(), event.getEventId(),
                    event.getReason(), event.getTimestamp(),
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidProtocolBufferException e) {
            logPoisonPill(record);
        }
    }

    // ── Fallback logger ───────────────────────────────────────────────────────

    /**
     * Fallback for poison pills — bytes that cannot be deserialized into a Protobuf message.
     * Logs raw metadata only since we cannot extract any business context.
     */
    private void logPoisonPill(ConsumerRecord<String, byte[]> record) {
        int byteLength = record.value() != null ? record.value().length : 0;
        log.error("[DLT] [POISON_PILL] Unreadable message — failed to deserialize | " +
                  "topic='{}', partition={}, offset={}, key='{}', valueBytes={}",
                record.topic(), record.partition(), record.offset(), record.key(), byteLength);
    }
}

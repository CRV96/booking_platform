package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.notification_service.constants.NotificationConst;
import com.booking.platform.notification_service.email.EmailService;
import com.booking.platform.notification_service.constants.EmailTemplatesConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for event-domain lifecycle messages.
 *
 * <p>Each method listens to a single topic. Where we have enough data in the
 * event payload to send a useful email, we do so. Where recipient data (organizer
 * or attendee emails) requires a user-service lookup not yet available, we log
 * and leave a TODO for P3+.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Each {@code @KafkaListener} references its own {@code containerFactory} bean,
 *       which holds the correct {@link com.booking.platform.common.events.serialization.ProtobufDeserializer}
 *       parser for that message type.</li>
 *   <li>{@link ConsumerRecord} gives access to topic, partition, offset, and key
 *       for structured logging and debugging.</li>
 *   <li>All methods are void — Spring Kafka handles offset commit automatically
 *       after successful method return (at-least-once delivery semantics).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationConsumer {

    private final EmailService emailService;

    /**
     * Receives notification when a new event is created (status: DRAFT).
     * Log only — organizer's real email requires a user-service gRPC lookup (P3+).
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
        // TODO P3+: fetch organizer email from user-service, send "event draft created" notice
    }

    /**
     * Receives notification when an event's fields are modified.
     * Log only — attendee emails require querying booking-service (P3+).
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
        // TODO P3+: if dateTime or venue changed, notify confirmed attendees
    }

    /**
     * Receives notification when an event goes live (DRAFT → PUBLISHED).
     * Log only — follower/attendee emails require a user-service lookup (P3+).
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
        // TODO P3+: send "Event is now live" email to followers
    }

    /**
     * Sends an event-cancelled notification email when the organiser cancels an event.
     *
     * <p>The recipient is stubbed as a platform address because attendee emails
     * require a booking-service lookup (not yet available). In P3+, query all
     * confirmed bookings for this eventId and send to each attendee's real email.
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

        // TODO P3+: query booking-service for all confirmed attendees of this event,
        //           send each one the event-cancelled email with their refund details.
        // For now, send a single stub email so the template and flow can be verified in MailHog.
        String stubRecipient = String.format(NotificationConst.DevStubEmails.ATTENDEES_FORMAT, event.getEventId());

        emailService.sendHtml(
                stubRecipient,
                EmailTemplatesConst.EventCancelled.SUBJECT,
                EmailTemplatesConst.EventCancelled.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.EventCancelled.Vars.EVENT_ID,  event.getEventId(),
                        EmailTemplatesConst.EventCancelled.Vars.REASON,    event.getReason(),
                        EmailTemplatesConst.EventCancelled.Vars.TIMESTAMP, event.getTimestamp()
                )
        );
    }
}

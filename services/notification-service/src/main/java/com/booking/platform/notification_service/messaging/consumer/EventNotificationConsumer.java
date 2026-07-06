package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.notification_service.constants.NotificationConst;
import com.booking.platform.notification_service.email.EmailService;
import com.booking.platform.notification_service.constants.EmailTemplatesConst;
import com.booking.platform.notification_service.grpc.client.BookingServiceClient;
import com.booking.platform.notification_service.grpc.client.UserServiceClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kafka consumer for event-domain lifecycle messages.
 * <ul>
 *   <li>Each {@code @KafkaListener} references its own {@code containerFactory} bean,
 *       which holds the correct {@link com.booking.platform.common.events.serialization.ProtobufDeserializer}
 *       parser for that message type.</li>
 *   <li>{@link ConsumerRecord} gives access to topic, partition, offset, and key
 *       for structured logging and debugging.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationConsumer {

    private final EmailService emailService;
    private final UserServiceClient userServiceClient;
    private final BookingServiceClient bookingServiceClient;
    private static final String CONFIRMED_STATUS = "CONFIRMED";

    /**
     * Sends an "event draft created" email to the organizer when a new event is created.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_CREATED,
            containerFactory = "eventCreatedListenerFactory"
    )
    public void onEventCreated(ConsumerRecord<String, EventCreatedEvent> record) {
        EventCreatedEvent event = record.value();
        ApplicationLogger.logMessage(log, Level.INFO,
                "[EVENT_CREATED] eventId='{}', title='{}', category='{}', organizer='{}' | partition={}, offset={}",
                event.getEventId(), event.getTitle(), event.getCategory(), event.getOrganizerId(),
                record.partition(), record.offset());

        String organizerEmail = userServiceClient.getUserEmail(event.getOrganizerId());

        emailService.sendHtml(
                organizerEmail,
                EmailTemplatesConst.EventCreated.SUBJECT,
                EmailTemplatesConst.EventCreated.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.EventCreated.Vars.EVENT_ID,      event.getEventId(),
                        EmailTemplatesConst.EventCreated.Vars.TITLE,         event.getTitle(),
                        EmailTemplatesConst.EventCreated.Vars.CATEGORY,      event.getCategory(),
                        EmailTemplatesConst.EventCreated.Vars.VENUE_NAME,    event.getVenue().getName(),
                        EmailTemplatesConst.EventCreated.Vars.VENUE_CITY,    event.getVenue().getCity(),
                        EmailTemplatesConst.EventCreated.Vars.VENUE_COUNTRY, event.getVenue().getCountry(),
                        EmailTemplatesConst.EventCreated.Vars.TIMESTAMP,     event.getTimestamp()
                )
        );

        ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-created email to organizer '{}'", organizerEmail);
    }

    /** Fields that are relevant to attendees — only these trigger an update email. */
    private static final Set<String> ATTENDEE_RELEVANT_FIELDS = Set.of(
            "dateTime", "endDateTime", "timezone", "venue"
    );

    /**
     * Sends an "event details updated" email to confirmed attendees when
     * attendee-relevant fields (date, time, venue) are modified.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_UPDATED,
            containerFactory = "eventUpdatedListenerFactory"
    )
    public void onEventUpdated(ConsumerRecord<String, EventUpdatedEvent> record) {
        EventUpdatedEvent event = record.value();
        ApplicationLogger.logMessage(log, Level.INFO,
                "[EVENT_UPDATED] eventId='{}', changedFields={} | partition={}, offset={}",
                event.getEventId(), event.getChangedFieldsList(), record.partition(), record.offset());

        List<String> relevantChanges = event.getChangedFieldsList().stream()
                .filter(ATTENDEE_RELEVANT_FIELDS::contains)
                .toList();

        if (relevantChanges.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "No attendee-relevant fields changed for event '{}', skipping notification", event.getEventId());
            return;
        }

        final List<String> recipientIds = bookingServiceClient.getBookingAttendees(event.getEventId(), CONFIRMED_STATUS);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched {} confirmed attendees for event '{}'", recipientIds.size(), event.getEventId());

        if (recipientIds.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "No attendees to notify for updated event '{}'", event.getEventId());
            return;
        }

        final List<String> recipientEmails = userServiceClient.getUsersEmails(recipientIds);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched {} attendee emails for event '{}'", recipientEmails.size(), event.getEventId());

        for (String email : recipientEmails) {
            emailService.sendHtml(
                    email,
                    EmailTemplatesConst.EventUpdated.SUBJECT,
                    EmailTemplatesConst.EventUpdated.TEMPLATE,
                    Map.of(
                            EmailTemplatesConst.EventUpdated.Vars.EVENT_ID,       event.getEventId(),
                            EmailTemplatesConst.EventUpdated.Vars.CHANGED_FIELDS, relevantChanges,
                            EmailTemplatesConst.EventUpdated.Vars.TIMESTAMP,      event.getTimestamp()
                    )
            );
            ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-updated email to '{}'", email);
        }

        ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-updated email to {} attendees", recipientEmails.size());
    }

    /**
     * Sends an "event is now live" email to the organizer when an event is published.
     */
    @KafkaListener(
            topics = KafkaTopics.EVENT_PUBLISHED,
            containerFactory = "eventPublishedListenerFactory"
    )
    public void onEventPublished(ConsumerRecord<String, EventPublishedEvent> record) {
        EventPublishedEvent event = record.value();
        ApplicationLogger.logMessage(log, Level.INFO,
                "[EVENT_PUBLISHED] eventId='{}', title='{}', category='{}', dateTime='{}' | partition={}, offset={}",
                event.getEventId(), event.getTitle(), event.getCategory(), event.getDateTime(),
                record.partition(), record.offset());

        String organizerEmail = userServiceClient.getUserEmail(event.getOrganizerId());

        emailService.sendHtml(
                organizerEmail,
                EmailTemplatesConst.EventPublished.SUBJECT,
                EmailTemplatesConst.EventPublished.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.EventPublished.Vars.EVENT_ID,  event.getEventId(),
                        EmailTemplatesConst.EventPublished.Vars.TITLE,     event.getTitle(),
                        EmailTemplatesConst.EventPublished.Vars.CATEGORY,  event.getCategory(),
                        EmailTemplatesConst.EventPublished.Vars.DATE_TIME, event.getDateTime(),
                        EmailTemplatesConst.EventPublished.Vars.TIMESTAMP, event.getTimestamp()
                )
        );
        ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-published email to organizer '{}'", organizerEmail);
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
        ApplicationLogger.logMessage(log, Level.INFO,
                "[EVENT_CANCELLED] eventId='{}', reason='{}' | partition={}, offset={}",
                event.getEventId(), event.getReason(), record.partition(), record.offset());

        final List<String> recipientIds = bookingServiceClient.getBookingAttendees(event.getEventId(), CONFIRMED_STATUS);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched {} confirmed attendees for event '{}' from booking-service", recipientIds.size(), event.getEventId());

        if(recipientIds.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.DEBUG, "No attendees to notify for cancelled event '{}'", event.getEventId());
            return;
        }

        final List<String> recipientEmails = userServiceClient.getUsersEmails(recipientIds);
        ApplicationLogger.logMessage(log, Level.DEBUG, "Fetched {} attendee emails for event '{}' from user-service", recipientEmails.size(), event.getEventId());

        int counter = 0;

        for(String email : recipientEmails) {
            emailService.sendHtml(
                email,
                EmailTemplatesConst.EventCancelled.SUBJECT,
                EmailTemplatesConst.EventCancelled.TEMPLATE,
                Map.of(
                        EmailTemplatesConst.EventCancelled.Vars.EVENT_ID,  event.getEventId(),
                        EmailTemplatesConst.EventCancelled.Vars.REASON,    event.getReason(),
                        EmailTemplatesConst.EventCancelled.Vars.TIMESTAMP, event.getTimestamp()
                )
            );
            counter++;

            ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-cancelled email to '{}'", email);
        }

        ApplicationLogger.logMessage(log, Level.DEBUG, "Sent event-cancelled email to {} attendees", counter);

    }
}

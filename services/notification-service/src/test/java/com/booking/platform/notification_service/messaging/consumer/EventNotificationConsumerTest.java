package com.booking.platform.notification_service.messaging.consumer;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.VenueSnapshot;
import com.booking.platform.notification_service.constants.EmailTemplatesConst;
import com.booking.platform.notification_service.email.EmailService;
import com.booking.platform.notification_service.grpc.client.BookingServiceClient;
import com.booking.platform.notification_service.grpc.client.UserServiceClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventNotificationConsumerTest {

    @Mock private EmailService emailService;
    @Mock private UserServiceClient userServiceClient;
    @Mock private BookingServiceClient bookingServiceClient;

    @InjectMocks private EventNotificationConsumer consumer;

    // ── onEventCreated ────────────────────────────────────────────────────────

    @Test
    void onEventCreated_fetchesOrganizerEmailAndSendsEmail() {
        EventCreatedEvent event = EventCreatedEvent.newBuilder()
                .setEventId("ev-1").setTitle("Rock Fest").setCategory("MUSIC")
                .setOrganizerId("org-1").setTimestamp("2024-01-01T00:00:00Z")
                .setVenue(VenueSnapshot.newBuilder().setName("Arena").setCity("Berlin").setCountry("DE").build())
                .build();
        ConsumerRecord<String, EventCreatedEvent> record =
                new ConsumerRecord<>("events.event.created", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("org-1")).thenReturn("organizer@test.com");

        consumer.onEventCreated(record);

        verify(userServiceClient).getUserEmail("org-1");
        verify(emailService).sendHtml(
                eq("organizer@test.com"),
                eq(EmailTemplatesConst.EventCreated.SUBJECT),
                eq(EmailTemplatesConst.EventCreated.TEMPLATE),
                any());
    }

    @Test
    void onEventCreated_variablesContainVenueFields() {
        EventCreatedEvent event = EventCreatedEvent.newBuilder()
                .setEventId("ev-2").setTitle("Jazz Night").setCategory("JAZZ")
                .setOrganizerId("org-2").setTimestamp("2024-03-01T00:00:00Z")
                .setVenue(VenueSnapshot.newBuilder().setName("Blue Note").setCity("NYC").setCountry("US").build())
                .build();
        ConsumerRecord<String, EventCreatedEvent> record =
                new ConsumerRecord<>("events.event.created", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("org-2")).thenReturn("jazz@test.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onEventCreated(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars.get(EmailTemplatesConst.EventCreated.Vars.EVENT_ID)).isEqualTo("ev-2");
        assertThat(vars.get(EmailTemplatesConst.EventCreated.Vars.TITLE)).isEqualTo("Jazz Night");
        assertThat(vars.get(EmailTemplatesConst.EventCreated.Vars.VENUE_NAME)).isEqualTo("Blue Note");
        assertThat(vars.get(EmailTemplatesConst.EventCreated.Vars.VENUE_CITY)).isEqualTo("NYC");
        assertThat(vars.get(EmailTemplatesConst.EventCreated.Vars.VENUE_COUNTRY)).isEqualTo("US");
    }

    // ── onEventUpdated — relevant fields filter ───────────────────────────────

    @Test
    void onEventUpdated_irrelevantFieldsOnly_skipsNotification() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-1")
                .addChangedFields("description")
                .addChangedFields("title")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);

        consumer.onEventUpdated(record);

        verifyNoInteractions(bookingServiceClient);
        verifyNoInteractions(emailService);
    }

    @Test
    void onEventUpdated_relevantFieldPresent_fetchesAttendeesAndSendsEmails() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-2")
                .addChangedFields("description")
                .addChangedFields("dateTime")          // relevant
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-2", "CONFIRMED"))
                .thenReturn(List.of("u-1", "u-2"));
        when(userServiceClient.getUsersEmails(List.of("u-1", "u-2")))
                .thenReturn(List.of("a@test.com", "b@test.com"));

        consumer.onEventUpdated(record);

        verify(emailService, times(2)).sendHtml(anyString(),
                eq(EmailTemplatesConst.EventUpdated.SUBJECT),
                eq(EmailTemplatesConst.EventUpdated.TEMPLATE),
                any());
    }

    @Test
    void onEventUpdated_relevantFieldsFiltered_onlyRelevantInVariables() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-3")
                .addChangedFields("title")      // not relevant
                .addChangedFields("venue")      // relevant
                .addChangedFields("timezone")   // relevant
                .setTimestamp("2024-02-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-3", "CONFIRMED"))
                .thenReturn(List.of("u-1"));
        when(userServiceClient.getUsersEmails(List.of("u-1")))
                .thenReturn(List.of("x@test.com"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onEventUpdated(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        @SuppressWarnings("unchecked")
        List<String> changedFields = (List<String>) varsCaptor.getValue()
                .get(EmailTemplatesConst.EventUpdated.Vars.CHANGED_FIELDS);
        assertThat(changedFields).containsExactlyInAnyOrder("venue", "timezone");
        assertThat(changedFields).doesNotContain("title");
    }

    @Test
    void onEventUpdated_noAttendees_skipsEmailSend() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-4")
                .addChangedFields("dateTime")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-4", "CONFIRMED"))
                .thenReturn(List.of());

        consumer.onEventUpdated(record);

        verifyNoInteractions(userServiceClient);
        verifyNoInteractions(emailService);
    }

    @Test
    void onEventUpdated_allRelevantFieldNames_triggerNotification() {
        // All four relevant fields: dateTime, endDateTime, timezone, venue
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-5")
                .addChangedFields("dateTime")
                .addChangedFields("endDateTime")
                .addChangedFields("timezone")
                .addChangedFields("venue")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-5", "CONFIRMED"))
                .thenReturn(List.of("u-1"));
        when(userServiceClient.getUsersEmails(List.of("u-1")))
                .thenReturn(List.of("g@test.com"));

        consumer.onEventUpdated(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), any());
    }

    @Test
    void onEventUpdated_sendsOneEmailPerAttendee() {
        EventUpdatedEvent event = EventUpdatedEvent.newBuilder()
                .setEventId("ev-6")
                .addChangedFields("venue")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventUpdatedEvent> record =
                new ConsumerRecord<>("events.event.updated", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-6", "CONFIRMED"))
                .thenReturn(List.of("u-1", "u-2", "u-3"));
        when(userServiceClient.getUsersEmails(List.of("u-1", "u-2", "u-3")))
                .thenReturn(List.of("a@t.com", "b@t.com", "c@t.com"));

        consumer.onEventUpdated(record);

        verify(emailService, times(3)).sendHtml(anyString(), anyString(), anyString(), any());
    }

    // ── onEventPublished ──────────────────────────────────────────────────────

    @Test
    void onEventPublished_sendsEmailToOrganizer() {
        EventPublishedEvent event = EventPublishedEvent.newBuilder()
                .setEventId("ev-1").setTitle("Summer Fest").setCategory("MUSIC")
                .setOrganizerId("org-1").setDateTime("2024-07-01T18:00:00Z")
                .setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventPublishedEvent> record =
                new ConsumerRecord<>("events.event.published", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("org-1")).thenReturn("org@test.com");

        consumer.onEventPublished(record);

        verify(emailService).sendHtml(
                eq("org@test.com"),
                eq(EmailTemplatesConst.EventPublished.SUBJECT),
                eq(EmailTemplatesConst.EventPublished.TEMPLATE),
                any());
    }

    @Test
    void onEventPublished_variablesContainAllFields() {
        EventPublishedEvent event = EventPublishedEvent.newBuilder()
                .setEventId("ev-9").setTitle("Tech Conf").setCategory("TECH")
                .setOrganizerId("org-9").setDateTime("2024-09-10T09:00:00Z")
                .setTimestamp("2024-08-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventPublishedEvent> record =
                new ConsumerRecord<>("events.event.published", 0, 1L, "key", event);
        when(userServiceClient.getUserEmail("org-9")).thenReturn("tech@test.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onEventPublished(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars.get(EmailTemplatesConst.EventPublished.Vars.EVENT_ID)).isEqualTo("ev-9");
        assertThat(vars.get(EmailTemplatesConst.EventPublished.Vars.TITLE)).isEqualTo("Tech Conf");
        assertThat(vars.get(EmailTemplatesConst.EventPublished.Vars.CATEGORY)).isEqualTo("TECH");
        assertThat(vars.get(EmailTemplatesConst.EventPublished.Vars.DATE_TIME)).isEqualTo("2024-09-10T09:00:00Z");
    }

    // ── onEventCancelled ──────────────────────────────────────────────────────

    @Test
    void onEventCancelled_noAttendees_skipsEmailSend() {
        EventCancelledEvent event = EventCancelledEvent.newBuilder()
                .setEventId("ev-1").setReason("Venue flooded").setTimestamp("2024-01-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventCancelledEvent> record =
                new ConsumerRecord<>("events.event.cancelled", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-1", "CONFIRMED"))
                .thenReturn(List.of());

        consumer.onEventCancelled(record);

        verifyNoInteractions(userServiceClient);
        verifyNoInteractions(emailService);
    }

    @Test
    void onEventCancelled_withAttendees_sendsEmailToEach() {
        EventCancelledEvent event = EventCancelledEvent.newBuilder()
                .setEventId("ev-2").setReason("Organizer sick").setTimestamp("2024-03-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventCancelledEvent> record =
                new ConsumerRecord<>("events.event.cancelled", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-2", "CONFIRMED"))
                .thenReturn(List.of("u-1", "u-2"));
        when(userServiceClient.getUsersEmails(List.of("u-1", "u-2")))
                .thenReturn(List.of("alice@test.com", "bob@test.com"));

        consumer.onEventCancelled(record);

        verify(emailService, times(2)).sendHtml(anyString(),
                eq(EmailTemplatesConst.EventCancelled.SUBJECT),
                eq(EmailTemplatesConst.EventCancelled.TEMPLATE),
                any());
    }

    @Test
    void onEventCancelled_variablesContainEventFields() {
        EventCancelledEvent event = EventCancelledEvent.newBuilder()
                .setEventId("ev-3").setReason("Low attendance").setTimestamp("2024-04-01T00:00:00Z")
                .build();
        ConsumerRecord<String, EventCancelledEvent> record =
                new ConsumerRecord<>("events.event.cancelled", 0, 1L, "key", event);
        when(bookingServiceClient.getBookingAttendees("ev-3", "CONFIRMED"))
                .thenReturn(List.of("u-1"));
        when(userServiceClient.getUsersEmails(List.of("u-1")))
                .thenReturn(List.of("sole@test.com"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        consumer.onEventCancelled(record);

        verify(emailService).sendHtml(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars.get(EmailTemplatesConst.EventCancelled.Vars.EVENT_ID)).isEqualTo("ev-3");
        assertThat(vars.get(EmailTemplatesConst.EventCancelled.Vars.REASON)).isEqualTo("Low attendance");
        assertThat(vars.get(EmailTemplatesConst.EventCancelled.Vars.TIMESTAMP)).isEqualTo("2024-04-01T00:00:00Z");
    }
}

package com.booking.platform.event_service.messaging;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.messaging.publisher.impl.KafkaEventPublisher;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaEventPublisher}.
 *
 * <p>Verifies that each publish method:
 * <ul>
 *   <li>Sends to the correct Kafka topic</li>
 *   <li>Uses the event ID as the message key</li>
 *   <li>Includes the expected fields in the Protobuf payload</li>
 * </ul>
 *
 * <p>The {@link KafkaTemplate} is mocked — no broker is needed.
 * The returned {@link CompletableFuture} is pre-completed so the
 * async callback executes synchronously within the test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisher Unit Tests")
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, MessageLite> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<MessageLite> messageCaptor;

    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate);
        // Return a completed future so whenComplete() runs immediately in tests
        when(kafkaTemplate.send(anyString(), anyString(), any(MessageLite.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // =========================================================================
    // Test data helpers
    // =========================================================================

    private EventDocument buildEvent() {
        return EventDocument.builder()
                .id("event-123")
                .title("Rock Night")
                .category(EventCategory.CONCERT)
                .status(EventStatus.DRAFT)
                .dateTime(Instant.parse("2027-06-15T18:00:00Z"))
                .timezone("UTC")
                .venue(VenueInfo.builder()
                        .name("Arena Bucharest")
                        .city("Bucharest")
                        .country("Romania")
                        .build())
                .organizer(OrganizerInfo.builder()
                        .userId("organizer-001")
                        .name("Alice Smith")
                        .email("alice@example.com")
                        .build())
                .build();
    }

    // =========================================================================
    // publishEventCreated
    // =========================================================================

    @Nested
    @DisplayName("publishEventCreated")
    class PublishEventCreated {

        @Test
        @DisplayName("sends to events.event.created topic")
        void sendsToCorrectTopic() {
            publisher.publishEventCreated(buildEvent());

            verify(kafkaTemplate).send(eq(KafkaTopics.EVENT_CREATED), anyString(), any());
        }

        @Test
        @DisplayName("uses event ID as message key")
        void usesEventIdAsKey() {
            publisher.publishEventCreated(buildEvent());

            verify(kafkaTemplate).send(anyString(), eq("event-123"), any());
        }

        @Test
        @DisplayName("payload contains event ID, title, category and organizer ID")
        void payloadContainsExpectedFields() {
            publisher.publishEventCreated(buildEvent());

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());

            // Inspect the raw bytes — parse back as EventCreatedEvent
            var payload = (com.booking.platform.common.events.EventCreatedEvent) messageCaptor.getValue();
            assertThat(payload.getEventId()).isEqualTo("event-123");
            assertThat(payload.getTitle()).isEqualTo("Rock Night");
            assertThat(payload.getCategory()).isEqualTo("CONCERT");
            assertThat(payload.getOrganizerId()).isEqualTo("organizer-001");
            assertThat(payload.getVenue().getCity()).isEqualTo("Bucharest");
        }
    }

    // =========================================================================
    // publishEventUpdated
    // =========================================================================

    @Nested
    @DisplayName("publishEventUpdated")
    class PublishEventUpdated {

        @Test
        @DisplayName("sends to events.event.updated topic")
        void sendsToCorrectTopic() {
            publisher.publishEventUpdated(buildEvent(), List.of("title"));

            verify(kafkaTemplate).send(eq(KafkaTopics.EVENT_UPDATED), anyString(), any());
        }

        @Test
        @DisplayName("payload contains event ID and all changed fields")
        void payloadContainsChangedFields() {
            publisher.publishEventUpdated(buildEvent(), List.of("title", "description", "venue"));

            verify(kafkaTemplate).send(anyString(), anyString(), messageCaptor.capture());

            var payload = (com.booking.platform.common.events.EventUpdatedEvent) messageCaptor.getValue();
            assertThat(payload.getEventId()).isEqualTo("event-123");
            assertThat(payload.getChangedFieldsList()).containsExactly("title", "description", "venue");
        }
    }

    // =========================================================================
    // publishEventPublished
    // =========================================================================

    @Nested
    @DisplayName("publishEventPublished")
    class PublishEventPublished {

        @Test
        @DisplayName("sends to events.event.published topic")
        void sendsToCorrectTopic() {
            publisher.publishEventPublished(buildEvent());

            verify(kafkaTemplate).send(eq(KafkaTopics.EVENT_PUBLISHED), anyString(), any());
        }

        @Test
        @DisplayName("payload contains event ID and title")
        void payloadContainsEventIdAndTitle() {
            publisher.publishEventPublished(buildEvent());

            verify(kafkaTemplate).send(anyString(), anyString(), messageCaptor.capture());

            var payload = (com.booking.platform.common.events.EventPublishedEvent) messageCaptor.getValue();
            assertThat(payload.getEventId()).isEqualTo("event-123");
            assertThat(payload.getTitle()).isEqualTo("Rock Night");
        }
    }

    // =========================================================================
    // publishEventCancelled
    // =========================================================================

    @Nested
    @DisplayName("publishEventCancelled")
    class PublishEventCancelled {

        @Test
        @DisplayName("sends to events.event.cancelled topic")
        void sendsToCorrectTopic() {
            publisher.publishEventCancelled(buildEvent(), "Venue unavailable");

            verify(kafkaTemplate).send(eq(KafkaTopics.EVENT_CANCELLED), anyString(), any());
        }

        @Test
        @DisplayName("payload contains event ID and reason")
        void payloadContainsReason() {
            publisher.publishEventCancelled(buildEvent(), "Venue unavailable");

            verify(kafkaTemplate).send(anyString(), anyString(), messageCaptor.capture());

            var payload = (com.booking.platform.common.events.EventCancelledEvent) messageCaptor.getValue();
            assertThat(payload.getEventId()).isEqualTo("event-123");
            assertThat(payload.getReason()).isEqualTo("Venue unavailable");
        }

        @Test
        @DisplayName("null reason is converted to empty string in payload")
        void nullReason_convertedToEmptyString() {
            publisher.publishEventCancelled(buildEvent(), null);

            verify(kafkaTemplate).send(anyString(), anyString(), messageCaptor.capture());

            var payload = (com.booking.platform.common.events.EventCancelledEvent) messageCaptor.getValue();
            assertThat(payload.getReason()).isEmpty();
        }
    }
}

package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.UpdateEventRequest;
import com.booking.platform.event_service.base.BaseIntegrationTest;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.exception.EventNotFoundException;
import com.booking.platform.event_service.exception.InvalidEventStateException;
import com.booking.platform.event_service.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for EventService — covers CRUD lifecycle, state machine,
 * and Redis cache behaviour (hit, miss, eviction).
 *
 * <p>Uses real MongoDB and Redis containers via {@link BaseIntegrationTest}.
 * Each test starts with a clean database and empty caches.
 */
@DisplayName("EventService Integration Tests")
class EventServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventService eventService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private CreateEventRequest buildCreateRequest(String title) {
        return CreateEventRequest.newBuilder()
                .setTitle(title)
                .setDescription("A great event")
                .setCategory("CONCERT")
                .setDateTime(Instant.now().plus(10, ChronoUnit.DAYS).toString())
                .setTimezone("UTC")
                .setVenue(com.booking.platform.common.grpc.event.VenueInfo.newBuilder()
                        .setName("Arena")
                        .setCity("Bucharest")
                        .setCountry("Romania")
                        .build())
                .addSeatCategories(SeatCategoryInfo.newBuilder()
                        .setName("General")
                        .setPrice(50.0)
                        .setCurrency("USD")
                        .setTotalSeats(100)
                        .build())
                .build();
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    @Nested
    @DisplayName("createEvent")
    class CreateEvent {

        @Test
        @DisplayName("saves event as DRAFT and persists to MongoDB")
        void createEvent_savesDraftToMongo() {
            EventDocument created = eventService.createEvent(buildCreateRequest("Rock Night"), defaultOrganizer());

            assertThat(created.getId()).isNotNull();
            assertThat(created.getTitle()).isEqualTo("Rock Night");
            assertThat(created.getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(created.getCreatedAt()).isNotNull();

            // Verify it's actually in MongoDB
            assertThat(eventRepository.findById(created.getId())).isPresent();
        }

        @Test
        @DisplayName("sets organizer info from OrganizerDto")
        void createEvent_setsOrganizerInfo() {
            EventDocument created = eventService.createEvent(buildCreateRequest("Jazz Night"), defaultOrganizer());

            assertThat(created.getOrganizer().getUserId()).isEqualTo("user-001");
            assertThat(created.getOrganizer().getName()).isEqualTo("Alice Smith");
            assertThat(created.getOrganizer().getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("sets availableSeats equal to totalSeats on creation")
        void createEvent_setsAvailableSeatsEqualToTotal() {
            EventDocument created = eventService.createEvent(buildCreateRequest("Pop Festival"), defaultOrganizer());

            assertThat(created.getSeatCategories()).hasSize(1);
            assertThat(created.getSeatCategories().get(0).getAvailableSeats())
                    .isEqualTo(created.getSeatCategories().get(0).getTotalSeats());
        }
    }

    // =========================================================================
    // GET + CACHE
    // =========================================================================

    @Nested
    @DisplayName("getEvent — cache behaviour")
    class GetEvent {

        @Test
        @DisplayName("returns event from MongoDB on cache miss")
        void getEvent_cacheMiss_fetchesFromMongo() {
            EventDocument saved = buildAndSaveEvent("Cached Concert", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            EventDocument result = eventService.getEvent(saved.getId());

            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getTitle()).isEqualTo("Cached Concert");
        }

        @Test
        @DisplayName("second call returns from Redis cache — not MongoDB")
        void getEvent_secondCall_returnsFromCache() {
            EventDocument saved = buildAndSaveEvent("Cached Concert", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            // First call — populates cache
            eventService.getEvent(saved.getId());

            // Delete from MongoDB to prove second call uses cache
            eventRepository.deleteById(saved.getId());

            // Second call — should come from Redis, not hit MongoDB
            EventDocument fromCache = eventService.getEvent(saved.getId());
            assertThat(fromCache.getId()).isEqualTo(saved.getId());
        }

        @Test
        @DisplayName("throws EventNotFoundException when event does not exist")
        void getEvent_notFound_throwsException() {
            assertThatThrownBy(() -> eventService.getEvent("non-existent-id"))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("cache is populated after first getEvent call")
        void getEvent_populatesCache() {
            EventDocument saved = buildAndSaveEvent("Cached Concert", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            eventService.getEvent(saved.getId());

            Cache cache = cacheManager.getCache("event:detail");
            assertThat(cache).isNotNull();
            assertThat(cache.get(saved.getId())).isNotNull();
        }
    }

    // =========================================================================
    // UPDATE + CACHE EVICTION
    // =========================================================================

    @Nested
    @DisplayName("updateEvent — cache eviction")
    class UpdateEvent {

        @Test
        @DisplayName("updates title in MongoDB")
        void updateEvent_updatesTitle() {
            EventDocument saved = buildAndSaveEvent("Old Title", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            UpdateEventRequest request = UpdateEventRequest.newBuilder()
                    .setTitle("New Title")
                    .build();

            EventDocument updated = eventService.updateEvent(saved.getId(), request);

            assertThat(updated.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("evicts detail cache after update")
        void updateEvent_evictsDetailCache() {
            EventDocument saved = buildAndSaveEvent("Before Update", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            // Populate cache
            eventService.getEvent(saved.getId());
            assertThat(cacheManager.getCache("event:detail").get(saved.getId())).isNotNull();

            // Update — should evict cache
            eventService.updateEvent(saved.getId(),
                    UpdateEventRequest.newBuilder().setTitle("After Update").build());

            // Cache entry should be gone
            assertThat(cacheManager.getCache("event:detail").get(saved.getId())).isNull();
        }

        @Test
        @DisplayName("throws InvalidEventStateException when updating a cancelled event")
        void updateEvent_cancelledEvent_throwsException() {
            EventDocument saved = buildAndSaveEvent("Cancelled Event", EventCategory.CONCERT,
                    EventStatus.CANCELLED, futureDate(5));

            assertThatThrownBy(() -> eventService.updateEvent(saved.getId(),
                    UpdateEventRequest.newBuilder().setTitle("New Title").build()))
                    .isInstanceOf(InvalidEventStateException.class);
        }
    }

    // =========================================================================
    // PUBLISH
    // =========================================================================

    @Nested
    @DisplayName("publishEvent")
    class PublishEvent {

        @Test
        @DisplayName("transitions event from DRAFT to PUBLISHED")
        void publishEvent_draftToPublished() {
            EventDocument draft = buildAndSaveEvent("Draft Event", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            EventDocument published = eventService.publishEvent(draft.getId());

            assertThat(published.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }

        @Test
        @DisplayName("puts published event into detail cache immediately")
        void publishEvent_warmsDetailCache() {
            EventDocument draft = buildAndSaveEvent("Draft Event", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            eventService.publishEvent(draft.getId());

            // Cache should be populated via @CachePut
            Cache cache = cacheManager.getCache("event:detail");
            assertThat(cache).isNotNull();
            assertThat(cache.get(draft.getId())).isNotNull();
        }

        @Test
        @DisplayName("throws InvalidEventStateException when publishing a non-DRAFT event")
        void publishEvent_nonDraft_throwsException() {
            EventDocument published = buildAndSaveEvent("Already Published", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            assertThatThrownBy(() -> eventService.publishEvent(published.getId()))
                    .isInstanceOf(InvalidEventStateException.class);
        }

        @Test
        @DisplayName("throws ValidationException when dateTime is in the past")
        void publishEvent_pastDateTime_throwsException() {
            EventDocument pastEvent = buildAndSaveEvent("Past Event", EventCategory.CONCERT,
                    EventStatus.DRAFT, pastDate(1));

            assertThatThrownBy(() -> eventService.publishEvent(pastEvent.getId()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("past");
        }
    }

    // =========================================================================
    // CANCEL
    // =========================================================================

    @Nested
    @DisplayName("cancelEvent")
    class CancelEvent {

        @Test
        @DisplayName("transitions PUBLISHED event to CANCELLED")
        void cancelEvent_publishedToCancelled() {
            EventDocument published = buildAndSaveEvent("Live Concert", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            EventDocument cancelled = eventService.cancelEvent(published.getId(), "Organizer request");

            assertThat(cancelled.getStatus()).isEqualTo(EventStatus.CANCELLED);
        }

        @Test
        @DisplayName("evicts detail cache after cancellation")
        void cancelEvent_evictsDetailCache() {
            EventDocument published = buildAndSaveEvent("Live Concert", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            // Populate cache first
            eventService.getEvent(published.getId());
            assertThat(cacheManager.getCache("event:detail").get(published.getId())).isNotNull();

            // Cancel — should evict
            eventService.cancelEvent(published.getId(), "test");

            assertThat(cacheManager.getCache("event:detail").get(published.getId())).isNull();
        }

        @Test
        @DisplayName("throws InvalidEventStateException when cancelling an already cancelled event")
        void cancelEvent_alreadyCancelled_throwsException() {
            EventDocument cancelled = buildAndSaveEvent("Cancelled", EventCategory.CONCERT,
                    EventStatus.CANCELLED, futureDate(5));

            assertThatThrownBy(() -> eventService.cancelEvent(cancelled.getId(), "double cancel"))
                    .isInstanceOf(InvalidEventStateException.class);
        }
    }

    // =========================================================================
    // KAFKA PUBLISHING — verifies publisher is called at each lifecycle step
    // =========================================================================

    @Nested
    @DisplayName("Kafka publishing")
    class KafkaPublishing {

        @Test
        @DisplayName("publishEventCreated is called after createEvent")
        void createEvent_publishesCreatedEvent() {
            eventService.createEvent(buildCreateRequest("Kafka Concert"), defaultOrganizer());

            verify(eventPublisher).publishEventCreated(any(EventDocument.class));
        }

        @Test
        @DisplayName("publishEventUpdated is called with correct changedFields after updateEvent")
        void updateEvent_publishesUpdatedEventWithChangedFields() {
            EventDocument saved = buildAndSaveEvent("Original Title", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            UpdateEventRequest request = UpdateEventRequest.newBuilder()
                    .setTitle("Updated Title")
                    .setDescription("Updated description")
                    .build();

            eventService.updateEvent(saved.getId(), request);

            verify(eventPublisher).publishEventUpdated(
                    any(EventDocument.class),
                    eq(List.of("title", "description"))
            );
        }

        @Test
        @DisplayName("publishEventPublished is called after publishEvent")
        void publishEvent_publishesPublishedEvent() {
            EventDocument draft = buildAndSaveEvent("Upcoming Concert", EventCategory.CONCERT,
                    EventStatus.DRAFT, futureDate(5));

            eventService.publishEvent(draft.getId());

            verify(eventPublisher).publishEventPublished(any(EventDocument.class));
        }

        @Test
        @DisplayName("publishEventCancelled is called with reason after cancelEvent")
        void cancelEvent_publishesCancelledEventWithReason() {
            EventDocument published = buildAndSaveEvent("Going Away", EventCategory.CONCERT,
                    EventStatus.PUBLISHED, futureDate(5));

            eventService.cancelEvent(published.getId(), "Venue unavailable");

            verify(eventPublisher).publishEventCancelled(
                    any(EventDocument.class),
                    eq("Venue unavailable")
            );
        }

        @Test
        @DisplayName("no event is published when createEvent validation fails")
        void createEvent_validationFailure_doesNotPublish() {
            CreateEventRequest invalidRequest = CreateEventRequest.newBuilder()
                    .setTitle("") // blank title — validation will reject
                    .setCategory("CONCERT")
                    .setDateTime(Instant.now().plus(5, ChronoUnit.DAYS).toString())
                    .setTimezone("UTC")
                    .setVenue(com.booking.platform.common.grpc.event.VenueInfo.newBuilder()
                            .setName("Arena").setCity("Bucharest").setCountry("Romania").build())
                    .addSeatCategories(SeatCategoryInfo.newBuilder()
                            .setName("General").setPrice(50.0).setCurrency("USD").setTotalSeats(100).build())
                    .build();

            assertThatThrownBy(() -> eventService.createEvent(invalidRequest, defaultOrganizer()));

            verify(eventPublisher, never()).publishEventCreated(any());
        }

        @Test
        @DisplayName("no event is published when cancelEvent throws InvalidEventStateException")
        void cancelEvent_invalidState_doesNotPublish() {
            EventDocument cancelled = buildAndSaveEvent("Already Done", EventCategory.CONCERT,
                    EventStatus.CANCELLED, futureDate(5));

            assertThatThrownBy(() -> eventService.cancelEvent(cancelled.getId(), "reason"))
                    .isInstanceOf(InvalidEventStateException.class);

            verify(eventPublisher, never()).publishEventCancelled(any(), anyString());
        }
    }
}

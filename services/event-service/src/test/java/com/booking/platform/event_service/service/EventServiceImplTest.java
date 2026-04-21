package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.*;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.exception.EventNotFoundException;
import com.booking.platform.event_service.exception.InsufficientSeatsException;
import com.booking.platform.event_service.exception.InvalidEventStateException;
import com.booking.platform.event_service.exception.ValidationException;
import com.booking.platform.event_service.messaging.publisher.EventPublisher;
import com.booking.platform.event_service.properties.EventProperties;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.impl.EventServiceImpl;
import com.booking.platform.event_service.validator.EventValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock private EventRepository eventRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private EventValidator eventValidator;
    @Mock private EventPublisher eventPublisher;
    @Mock private EventProperties eventProperties;

    @InjectMocks private EventServiceImpl service;

    private static final String EVENT_ID = "ev-1";
    private static final OrganizerDto ORGANIZER = OrganizerDto.builder()
            .userId("u-1").name("Alice").email("alice@example.com").build();

    private EventDocument draftEvent() {
        return EventDocument.builder()
                .id(EVENT_ID)
                .title("Fest")
                .category(EventCategory.CONCERT)
                .status(EventStatus.DRAFT)
                .dateTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .timezone("UTC")
                .venue(VenueInfo.builder().name("Arena").city("Berlin").country("DE").build())
                .organizer(OrganizerInfo.builder().userId("u-1").name("Alice").email("alice@example.com").build())
                .seatCategories(List.of(
                        SeatCategory.builder().name("GA").price(50.0).currency("USD")
                                .totalSeats(100).availableSeats(100).build()))
                .build();
    }

    private CreateEventRequest validCreateRequest() {
        return CreateEventRequest.newBuilder()
                .setTitle("Fest")
                .setCategory("CONCERT")
                .setDateTime(Instant.now().plus(5, ChronoUnit.DAYS).toString())
                .setTimezone("UTC")
                .setVenue(com.booking.platform.common.grpc.event.VenueInfo.newBuilder()
                        .setName("Arena").setCity("Berlin").setCountry("DE").build())
                .addSeatCategories(SeatCategoryInfo.newBuilder()
                        .setName("GA").setPrice(50.0).setCurrency("USD").setTotalSeats(100).build())
                .build();
    }

    // ── createEvent ───────────────────────────────────────────────────────────

    @Test
    void createEvent_validatesRequest() {
        when(eventValidator.parseInstant(anyString(), eq("dateTime")))
                .thenReturn(Instant.now().plus(5, ChronoUnit.DAYS));
        when(eventRepository.save(any())).thenReturn(draftEvent());

        service.createEvent(validCreateRequest(), ORGANIZER);

        verify(eventValidator).validateCreateRequest(any());
    }

    @Test
    void createEvent_savesAndPublishes() {
        EventDocument saved = draftEvent();
        when(eventValidator.parseInstant(anyString(), eq("dateTime")))
                .thenReturn(Instant.now().plus(5, ChronoUnit.DAYS));
        when(eventRepository.save(any())).thenReturn(saved);

        EventDocument result = service.createEvent(validCreateRequest(), ORGANIZER);

        verify(eventRepository).save(any());
        verify(eventPublisher).publishEventCreated(saved);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createEvent_setsStatusToDraft() {
        when(eventValidator.parseInstant(anyString(), eq("dateTime")))
                .thenReturn(Instant.now().plus(5, ChronoUnit.DAYS));
        ArgumentCaptor<EventDocument> captor = ArgumentCaptor.forClass(EventDocument.class);
        when(eventRepository.save(captor.capture())).thenReturn(draftEvent());

        service.createEvent(validCreateRequest(), ORGANIZER);

        assertThat(captor.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void createEvent_mapsOrganizerFromDto() {
        when(eventValidator.parseInstant(anyString(), eq("dateTime")))
                .thenReturn(Instant.now().plus(5, ChronoUnit.DAYS));
        ArgumentCaptor<EventDocument> captor = ArgumentCaptor.forClass(EventDocument.class);
        when(eventRepository.save(captor.capture())).thenReturn(draftEvent());

        service.createEvent(validCreateRequest(), ORGANIZER);

        assertThat(captor.getValue().getOrganizer().getUserId()).isEqualTo("u-1");
        assertThat(captor.getValue().getOrganizer().getEmail()).isEqualTo("alice@example.com");
    }

    // ── getEvent ──────────────────────────────────────────────────────────────

    @Test
    void getEvent_found_returnsDocument() {
        EventDocument event = draftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThat(service.getEvent(EVENT_ID)).isSameAs(event);
    }

    @Test
    void getEvent_notFound_throwsEventNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEvent(EVENT_ID))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(EVENT_ID);
    }

    // ── updateEvent ───────────────────────────────────────────────────────────

    @Test
    void updateEvent_notFound_throwsEventNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEvent(EVENT_ID, UpdateEventRequest.newBuilder().build()))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void updateEvent_cancelledEvent_throwsInvalidEventState() {
        EventDocument cancelled = draftEvent();
        cancelled.setStatus(EventStatus.CANCELLED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.updateEvent(EVENT_ID, UpdateEventRequest.newBuilder().build()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void updateEvent_completedEvent_throwsInvalidEventState() {
        EventDocument completed = draftEvent();
        completed.setStatus(EventStatus.COMPLETED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.updateEvent(EVENT_ID, UpdateEventRequest.newBuilder().build()))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void updateEvent_updatesTitle_whenPresent() {
        EventDocument event = draftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateEventRequest request = UpdateEventRequest.newBuilder()
                .setEventId(EVENT_ID).setTitle("New Title").build();

        EventDocument result = service.updateEvent(EVENT_ID, request);

        assertThat(result.getTitle()).isEqualTo("New Title");
        verify(eventPublisher).publishEventUpdated(any(), argThat(fields -> fields.contains("title")));
    }

    @Test
    void updateEvent_noChanges_savesAndPublishes() {
        EventDocument event = draftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenReturn(event);

        service.updateEvent(EVENT_ID, UpdateEventRequest.newBuilder().setEventId(EVENT_ID).build());

        verify(eventRepository).save(event);
        verify(eventPublisher).publishEventUpdated(any(), any());
    }

    // ── publishEvent ──────────────────────────────────────────────────────────

    @Test
    void publishEvent_notFound_throwsEventNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publishEvent(EVENT_ID))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void publishEvent_alreadyPublished_throwsInvalidEventState() {
        EventDocument event = draftEvent();
        event.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publishEvent(EVENT_ID))
                .isInstanceOf(InvalidEventStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void publishEvent_validDraft_setsPublishedAndPublishesEvent() {
        EventDocument event = draftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventDocument result = service.publishEvent(EVENT_ID);

        assertThat(result.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventValidator).validateForPublish(event);
        verify(eventPublisher).publishEventPublished(result);
    }

    // ── cancelEvent ───────────────────────────────────────────────────────────

    @Test
    void cancelEvent_notFound_throwsEventNotFoundException() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEvent(EVENT_ID, "reason"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void cancelEvent_alreadyCancelled_throwsInvalidEventState() {
        EventDocument event = draftEvent();
        event.setStatus(EventStatus.CANCELLED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.cancelEvent(EVENT_ID, "reason"))
                .isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void cancelEvent_draft_setsStatusAndPublishes() {
        EventDocument event = draftEvent();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventDocument result = service.cancelEvent(EVENT_ID, "Too expensive");

        assertThat(result.getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(eventPublisher).publishEventCancelled(result, "Too expensive");
    }

    @Test
    void cancelEvent_published_setsStatusAndPublishes() {
        EventDocument event = draftEvent();
        event.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelEvent(EVENT_ID, null);

        verify(eventPublisher).publishEventCancelled(any(), isNull());
    }

    // ── searchEvents ──────────────────────────────────────────────────────────

    @Test
    void searchEvents_delegatesToMongoTemplate() {
        EventProperties.Pagination pagination = new EventProperties.Pagination(20, 100, 5000);
        when(eventProperties.pagination()).thenReturn(pagination);
        when(mongoTemplate.find(any(Query.class), eq(EventDocument.class))).thenReturn(List.of(draftEvent()));

        List<EventDocument> results = service.searchEvents(SearchEventsRequest.newBuilder().build());

        assertThat(results).hasSize(1);
        verify(mongoTemplate).find(any(Query.class), eq(EventDocument.class));
    }

    @Test
    void searchEvents_appliesDefaultPageSize() {
        EventProperties.Pagination pagination = new EventProperties.Pagination(20, 100, 5000);
        when(eventProperties.pagination()).thenReturn(pagination);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(EventDocument.class))).thenReturn(List.of());

        service.searchEvents(SearchEventsRequest.newBuilder().build());

        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    void searchEvents_capsPageSizeAtMax() {
        EventProperties.Pagination pagination = new EventProperties.Pagination(20, 100, 5000);
        when(eventProperties.pagination()).thenReturn(pagination);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(mongoTemplate.find(queryCaptor.capture(), eq(EventDocument.class))).thenReturn(List.of());

        service.searchEvents(SearchEventsRequest.newBuilder().setPageSize(9999).build());

        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(100);
    }

    // ── updateSeatAvailability ────────────────────────────────────────────────

    @Test
    void updateSeatAvailability_success_returnsUpdatedDocument() {
        EventDocument updated = draftEvent();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(EventDocument.class))).thenReturn(updated);

        EventDocument result = service.updateSeatAvailability(EVENT_ID, "GA", -2);

        assertThat(result).isSameAs(updated);
    }

    @Test
    void updateSeatAvailability_nullResult_eventNotFound_throwsEventNotFoundException() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(EventDocument.class))).thenReturn(null);
        when(eventRepository.existsById(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateSeatAvailability(EVENT_ID, "GA", -2))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(EVENT_ID);
    }

    @Test
    void updateSeatAvailability_nullResult_eventExists_throwsInsufficientSeats() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(EventDocument.class))).thenReturn(null);
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.updateSeatAvailability(EVENT_ID, "VIP", -5))
                .isInstanceOf(InsufficientSeatsException.class);
    }

    @Test
    void updateSeatAvailability_increment_doesNotCheckAvailability() {
        EventDocument updated = draftEvent();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(EventDocument.class))).thenReturn(updated);

        // delta > 0 is a release — should succeed without checking availability
        EventDocument result = service.updateSeatAvailability(EVENT_ID, "GA", 2);

        assertThat(result).isSameAs(updated);
        verify(eventRepository, never()).existsById(any());
    }
}

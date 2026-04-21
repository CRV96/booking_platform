package com.booking.platform.event_service.grpc;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.event.*;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.OrganizerInfo;
import com.booking.platform.event_service.document.SeatCategory;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.exception.PermissionDeniedException;
import com.booking.platform.event_service.mapper.EventMapper;
import com.booking.platform.event_service.properties.EventProperties;
import com.booking.platform.event_service.service.EventService;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventGrpcServiceTest {

    @Mock private EventService eventService;
    @Mock private EventMapper eventMapper;
    @Mock private EventProperties eventProperties;
    @Mock private StreamObserver<EventResponse> eventObserver;
    @Mock private StreamObserver<SearchEventsResponse> searchObserver;
    @Mock private StreamObserver<UpdateSeatAvailabilityResponse> seatObserver;

    @InjectMocks private EventGrpcService grpcService;

    private static final String EVENT_ID = "ev-1";
    private static final String USER_ID = "u-1";

    private Context previousContext;

    /** Attach a gRPC Context with employee role and userId before each test. */
    @BeforeEach
    void attachEmployeeContext() {
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, USER_ID)
                .withValue(GrpcUserContext.USERNAME, "Alice")
                .withValue(GrpcUserContext.EMAIL, "alice@example.com")
                .withValue(GrpcUserContext.ROLES, List.of("employee"));
        previousContext = ctx.attach();

        when(eventProperties.pagination()).thenReturn(new EventProperties.Pagination(20, 100, 5000));
        when(eventMapper.toProto(any())).thenReturn(EventInfo.getDefaultInstance());
        when(eventMapper.toProtoList(any())).thenReturn(List.of());
    }

    @AfterEach
    void detachContext() {
        Context.current().detach(previousContext);
    }

    private EventDocument eventOwnedBy(String userId) {
        return EventDocument.builder()
                .id(EVENT_ID)
                .title("Fest")
                .category(EventCategory.CONCERT)
                .status(EventStatus.DRAFT)
                .dateTime(Instant.now().plus(5, ChronoUnit.DAYS))
                .timezone("UTC")
                .venue(VenueInfo.builder().name("Arena").city("Berlin").country("DE").build())
                .organizer(OrganizerInfo.builder().userId(userId).name("Alice").email("alice@example.com").build())
                .seatCategories(List.of(SeatCategory.builder().name("GA").price(50.0).currency("USD")
                        .totalSeats(100).availableSeats(80).build()))
                .build();
    }

    // ── getEvent (public) ────────────────────────────────────────────────────

    @Test
    void getEvent_delegatesToService_andCompletes() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);

        grpcService.getEvent(GetEventRequest.newBuilder().setEventId(EVENT_ID).build(), eventObserver);

        verify(eventService).getEvent(EVENT_ID);
        verify(eventObserver).onNext(any());
        verify(eventObserver).onCompleted();
    }

    // ── searchEvents (public) ─────────────────────────────────────────────────

    @Test
    void searchEvents_returnsResponseWithPagination() {
        when(eventService.searchEvents(any())).thenReturn(List.of(eventOwnedBy(USER_ID)));
        when(eventMapper.toProtoList(any())).thenReturn(List.of(EventInfo.getDefaultInstance()));

        grpcService.searchEvents(SearchEventsRequest.newBuilder().build(), searchObserver);

        ArgumentCaptor<SearchEventsResponse> captor = ArgumentCaptor.forClass(SearchEventsResponse.class);
        verify(searchObserver).onNext(captor.capture());
        verify(searchObserver).onCompleted();

        SearchEventsResponse response = captor.getValue();
        assertThat(response.getEventsCount()).isEqualTo(1);
        assertThat(response.getPagination().getTotalCount()).isEqualTo(1);
    }

    @Test
    void searchEvents_defaultPageSize_usesProperties() {
        when(eventService.searchEvents(any())).thenReturn(List.of());

        grpcService.searchEvents(SearchEventsRequest.newBuilder().build(), searchObserver);

        ArgumentCaptor<SearchEventsResponse> captor = ArgumentCaptor.forClass(SearchEventsResponse.class);
        verify(searchObserver).onNext(captor.capture());
        assertThat(captor.getValue().getPagination().getPageSize()).isEqualTo(20);
    }

    // ── createEvent (employee only) ───────────────────────────────────────────

    @Test
    void createEvent_withEmployeeRole_delegatesToService() {
        EventDocument saved = eventOwnedBy(USER_ID);
        when(eventService.createEvent(any(), any())).thenReturn(saved);

        grpcService.createEvent(CreateEventRequest.newBuilder().setTitle("Fest").build(), eventObserver);

        verify(eventService).createEvent(any(), any());
        verify(eventObserver).onCompleted();
    }

    @Test
    void createEvent_withoutEmployeeRole_throwsPermissionDenied() {
        detachContext();
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, USER_ID)
                .withValue(GrpcUserContext.ROLES, List.of("customer"));
        previousContext = ctx.attach();

        assertThatThrownBy(() -> grpcService.createEvent(
                CreateEventRequest.newBuilder().build(), eventObserver))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(eventService);
    }

    @Test
    void createEvent_mapsOrganizerFromContext() {
        EventDocument saved = eventOwnedBy(USER_ID);
        when(eventService.createEvent(any(), argThat(org ->
                org.userId().equals(USER_ID) && org.email().equals("alice@example.com"))))
                .thenReturn(saved);

        grpcService.createEvent(CreateEventRequest.newBuilder().build(), eventObserver);

        verify(eventService).createEvent(any(), argThat(org -> org.userId().equals(USER_ID)));
    }

    // ── updateEvent (employee + ownership) ───────────────────────────────────

    @Test
    void updateEvent_ownerWithEmployeeRole_delegates() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);
        when(eventService.updateEvent(eq(EVENT_ID), any())).thenReturn(event);

        grpcService.updateEvent(UpdateEventRequest.newBuilder().setEventId(EVENT_ID).build(), eventObserver);

        verify(eventService).updateEvent(eq(EVENT_ID), any());
        verify(eventObserver).onCompleted();
    }

    @Test
    void updateEvent_wrongOwner_throwsPermissionDenied() {
        EventDocument event = eventOwnedBy("other-user");
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);

        assertThatThrownBy(() -> grpcService.updateEvent(
                UpdateEventRequest.newBuilder().setEventId(EVENT_ID).build(), eventObserver))
                .isInstanceOf(PermissionDeniedException.class);

        verify(eventService, never()).updateEvent(any(), any());
    }

    // ── publishEvent (employee + ownership) ──────────────────────────────────

    @Test
    void publishEvent_ownerWithEmployeeRole_delegates() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);
        when(eventService.publishEvent(EVENT_ID)).thenReturn(event);

        grpcService.publishEvent(PublishEventRequest.newBuilder().setEventId(EVENT_ID).build(), eventObserver);

        verify(eventService).publishEvent(EVENT_ID);
        verify(eventObserver).onCompleted();
    }

    @Test
    void publishEvent_withoutRole_throwsPermissionDenied() {
        detachContext();
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, USER_ID)
                .withValue(GrpcUserContext.ROLES, List.of());
        previousContext = ctx.attach();

        assertThatThrownBy(() -> grpcService.publishEvent(
                PublishEventRequest.newBuilder().setEventId(EVENT_ID).build(), eventObserver))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // ── cancelEvent (employee + ownership) ───────────────────────────────────

    @Test
    void cancelEvent_ownerWithEmployeeRole_delegates() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);
        when(eventService.cancelEvent(eq(EVENT_ID), any())).thenReturn(event);

        grpcService.cancelEvent(CancelEventRequest.newBuilder()
                .setEventId(EVENT_ID).setReason("Rain").build(), eventObserver);

        verify(eventService).cancelEvent(EVENT_ID, "Rain");
    }

    @Test
    void cancelEvent_blankReason_passesNull() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.getEvent(EVENT_ID)).thenReturn(event);
        when(eventService.cancelEvent(eq(EVENT_ID), isNull())).thenReturn(event);

        grpcService.cancelEvent(CancelEventRequest.newBuilder()
                .setEventId(EVENT_ID).setReason("   ").build(), eventObserver);

        verify(eventService).cancelEvent(EVENT_ID, null);
    }

    // ── updateSeatAvailability (internal) ────────────────────────────────────

    @Test
    void updateSeatAvailability_returnsRemainingSeats() {
        EventDocument event = eventOwnedBy(USER_ID); // GA: available=80
        when(eventService.updateSeatAvailability(EVENT_ID, "GA", -2)).thenReturn(event);

        grpcService.updateSeatAvailability(UpdateSeatAvailabilityRequest.newBuilder()
                .setEventId(EVENT_ID).setSeatCategoryName("GA").setDelta(-2).build(), seatObserver);

        ArgumentCaptor<UpdateSeatAvailabilityResponse> captor =
                ArgumentCaptor.forClass(UpdateSeatAvailabilityResponse.class);
        verify(seatObserver).onNext(captor.capture());
        verify(seatObserver).onCompleted();

        assertThat(captor.getValue().isInitialized()).isTrue();
        assertThat(captor.getValue().getSuccess()).isTrue();
        assertThat(captor.getValue().getRemainingSeats()).isEqualTo(80);
    }

    @Test
    void updateSeatAvailability_unknownCategory_returnsZeroSeats() {
        EventDocument event = eventOwnedBy(USER_ID);
        when(eventService.updateSeatAvailability(EVENT_ID, "VIP", 1)).thenReturn(event);

        grpcService.updateSeatAvailability(UpdateSeatAvailabilityRequest.newBuilder()
                .setEventId(EVENT_ID).setSeatCategoryName("VIP").setDelta(1).build(), seatObserver);

        ArgumentCaptor<UpdateSeatAvailabilityResponse> captor =
                ArgumentCaptor.forClass(UpdateSeatAvailabilityResponse.class);
        verify(seatObserver).onNext(captor.capture());
        assertThat(captor.getValue().getRemainingSeats()).isEqualTo(0);
    }
}

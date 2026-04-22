package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.event.*;
import com.booking.platform.graphql_gateway.dto.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceClientImplTest {

    @Mock private EventServiceGrpc.EventServiceBlockingStub stub;

    private EventServiceClientImpl client;

    private final EventResponse defaultResponse = EventResponse.getDefaultInstance();

    @BeforeEach
    void setUp() {
        client = new EventServiceClientImpl();
        ReflectionTestUtils.setField(client, "eventServiceStub", stub);
        when(stub.createEvent(any())).thenReturn(defaultResponse);
        when(stub.getEvent(any())).thenReturn(defaultResponse);
        when(stub.updateEvent(any())).thenReturn(defaultResponse);
        when(stub.publishEvent(any())).thenReturn(defaultResponse);
        when(stub.cancelEvent(any())).thenReturn(defaultResponse);
        when(stub.searchEvents(any())).thenReturn(SearchEventsResponse.getDefaultInstance());
    }

    private VenueInput venue() {
        return new VenueInput("Arena", "1 Main St", "London", "UK", 51.5, -0.1, 20000);
    }

    // ── createEvent ───────────────────────────────────────────────────────────

    @Test
    void createEvent_setsTitle() {
        CreateEventInput input = new CreateEventInput("Rock Fest", null, "CONCERT",
                "2026-06-01T20:00:00Z", venue(), null);
        client.createEvent(input);

        ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(stub).createEvent(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Rock Fest");
        assertThat(captor.getValue().getCategory()).isEqualTo("CONCERT");
    }

    @Test
    void createEvent_withDescription_setsDescription() {
        CreateEventInput input = new CreateEventInput("Fest", "Great event", "CONCERT",
                "2026-06-01T20:00:00Z", venue(), null);
        client.createEvent(input);

        ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(stub).createEvent(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("Great event");
    }

    @Test
    void createEvent_withSeatCategories_addsThemToRequest() {
        SeatCategoryInput seat = new SeatCategoryInput("Floor", 100.0, "USD", 500);
        CreateEventInput input = new CreateEventInput("Fest", null, "CONCERT",
                "2026-06-01T20:00:00Z", venue(), List.of(seat));
        client.createEvent(input);

        ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(stub).createEvent(captor.capture());
        assertThat(captor.getValue().getSeatCategoriesCount()).isEqualTo(1);
        assertThat(captor.getValue().getSeatCategories(0).getName()).isEqualTo("Floor");
    }

    @Test
    void createEvent_nullSeatCategories_addsNone() {
        CreateEventInput input = new CreateEventInput("Fest", null, "CONCERT",
                "2026-06-01T20:00:00Z", venue(), null);
        client.createEvent(input);

        ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(stub).createEvent(captor.capture());
        assertThat(captor.getValue().getSeatCategoriesCount()).isEqualTo(0);
    }

    // ── getEvent ──────────────────────────────────────────────────────────────

    @Test
    void getEvent_sendsCorrectEventId() {
        client.getEvent("ev-1");

        ArgumentCaptor<GetEventRequest> captor = ArgumentCaptor.forClass(GetEventRequest.class);
        verify(stub).getEvent(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("ev-1");
    }

    // ── updateEvent ───────────────────────────────────────────────────────────

    @Test
    void updateEvent_allFieldsNull_setsOnlyEventId() {
        client.updateEvent("ev-1", new UpdateEventInput(null, null, null, null, null, null));

        ArgumentCaptor<UpdateEventRequest> captor = ArgumentCaptor.forClass(UpdateEventRequest.class);
        verify(stub).updateEvent(captor.capture());
        UpdateEventRequest req = captor.getValue();
        assertThat(req.getEventId()).isEqualTo("ev-1");
        assertThat(req.hasTitle()).isFalse();
        assertThat(req.hasDescription()).isFalse();
    }

    @Test
    void updateEvent_withTitle_setsTitle() {
        client.updateEvent("ev-1", new UpdateEventInput("New Title", null, null, null, null, null));

        ArgumentCaptor<UpdateEventRequest> captor = ArgumentCaptor.forClass(UpdateEventRequest.class);
        verify(stub).updateEvent(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New Title");
    }

    // ── publishEvent ──────────────────────────────────────────────────────────

    @Test
    void publishEvent_sendsCorrectEventId() {
        client.publishEvent("ev-2");

        ArgumentCaptor<PublishEventRequest> captor = ArgumentCaptor.forClass(PublishEventRequest.class);
        verify(stub).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("ev-2");
    }

    // ── cancelEvent ───────────────────────────────────────────────────────────

    @Test
    void cancelEvent_sendsCorrectEventId() {
        client.cancelEvent("ev-3");

        ArgumentCaptor<CancelEventRequest> captor = ArgumentCaptor.forClass(CancelEventRequest.class);
        verify(stub).cancelEvent(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("ev-3");
    }

    // ── searchEvents ──────────────────────────────────────────────────────────

    @Test
    void searchEvents_withAllOptionals_setsThemOnRequest() {
        EventSearchRequest req = new EventSearchRequest("rock", "CONCERT", "London",
                "2026-01-01", "2026-12-31", 0, 10);
        client.searchEvents(req);

        ArgumentCaptor<SearchEventsRequest> captor = ArgumentCaptor.forClass(SearchEventsRequest.class);
        verify(stub).searchEvents(captor.capture());
        SearchEventsRequest sent = captor.getValue();
        assertThat(sent.getQuery()).isEqualTo("rock");
        assertThat(sent.getCategory()).isEqualTo("CONCERT");
        assertThat(sent.getCity()).isEqualTo("London");
        assertThat(sent.getDateFrom()).isEqualTo("2026-01-01");
        assertThat(sent.getDateTo()).isEqualTo("2026-12-31");
    }

    @Test
    void searchEvents_allOptionalsNull_notSetOnRequest() {
        EventSearchRequest req = new EventSearchRequest(null, null, null, null, null, 0, 10);
        client.searchEvents(req);

        ArgumentCaptor<SearchEventsRequest> captor = ArgumentCaptor.forClass(SearchEventsRequest.class);
        verify(stub).searchEvents(captor.capture());
        SearchEventsRequest sent = captor.getValue();
        assertThat(sent.hasQuery()).isFalse();
        assertThat(sent.hasCategory()).isFalse();
        assertThat(sent.hasCity()).isFalse();
    }
}

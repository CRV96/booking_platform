package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.SearchEventsResponse;
import com.booking.platform.graphql_gateway.dto.event.CreateEventInput;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.dto.event.EventConnection;
import com.booking.platform.graphql_gateway.dto.event.UpdateEventInput;
import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventResolverTest {

    @Mock private EventClient eventClient;
    @Mock private AuthService authService;

    @InjectMocks private EventResolver resolver;

    private static final EventResponse EVENT_RESPONSE = EventResponse.newBuilder()
            .setEvent(EventInfo.newBuilder().setId("ev-1").setTitle("Test").build())
            .build();

    // ── event (public query) ──────────────────────────────────────────────────

    @Test
    void event_delegatesToClient() {
        when(eventClient.getEvent("ev-1")).thenReturn(EVENT_RESPONSE);

        Event result = resolver.event("ev-1");

        verify(eventClient).getEvent("ev-1");
        assertThat(result.id()).isEqualTo("ev-1");
    }

    @Test
    void event_requiresNoAuth() {
        when(eventClient.getEvent(any())).thenReturn(EVENT_RESPONSE);

        resolver.event("ev-1");

        verifyNoInteractions(authService);
    }

    // ── events (public query) ─────────────────────────────────────────────────

    @Test
    void events_defaultsPageToZeroAndPageSizeToTwenty() {
        when(eventClient.searchEvents(any())).thenReturn(SearchEventsResponse.getDefaultInstance());

        resolver.events(null, null, null, null, null, null, null);

        ArgumentCaptor<com.booking.platform.graphql_gateway.dto.event.EventSearchRequest> captor =
                ArgumentCaptor.forClass(com.booking.platform.graphql_gateway.dto.event.EventSearchRequest.class);
        verify(eventClient).searchEvents(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(0);
        assertThat(captor.getValue().pageSize()).isEqualTo(20);
    }

    @Test
    void events_passesSearchParamsToClient() {
        when(eventClient.searchEvents(any())).thenReturn(SearchEventsResponse.getDefaultInstance());

        resolver.events("jazz", "MUSIC", "Berlin", "2024-01-01", "2024-12-31", 2, 10);

        ArgumentCaptor<com.booking.platform.graphql_gateway.dto.event.EventSearchRequest> captor =
                ArgumentCaptor.forClass(com.booking.platform.graphql_gateway.dto.event.EventSearchRequest.class);
        verify(eventClient).searchEvents(captor.capture());
        var req = captor.getValue();
        assertThat(req.query()).isEqualTo("jazz");
        assertThat(req.category()).isEqualTo("MUSIC");
        assertThat(req.city()).isEqualTo("Berlin");
        assertThat(req.page()).isEqualTo(2);
        assertThat(req.pageSize()).isEqualTo(10);
    }

    @Test
    void events_mapsResponseToConnection() {
        when(eventClient.searchEvents(any())).thenReturn(SearchEventsResponse.getDefaultInstance());

        EventConnection conn = resolver.events(null, null, null, null, null, null, null);

        assertThat(conn).isNotNull();
        assertThat(conn.events()).isEmpty();
    }

    // ── createEvent (employee only) ───────────────────────────────────────────

    @Test
    void createEvent_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.createEvent(mock(CreateEventInput.class)))
                .isInstanceOf(GraphQLException.class)
                .extracting(ex -> ((GraphQLException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(eventClient);
    }

    @Test
    void createEvent_whenAuthorized_delegatesToClient() {
        CreateEventInput input = mock(CreateEventInput.class);
        when(eventClient.createEvent(input)).thenReturn(EVENT_RESPONSE);

        Event result = resolver.createEvent(input);

        verify(authService).requireRole("employee");
        verify(eventClient).createEvent(input);
        assertThat(result.id()).isEqualTo("ev-1");
    }

    // ── updateEvent (employee only) ───────────────────────────────────────────

    @Test
    void updateEvent_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.updateEvent("ev-1", mock(UpdateEventInput.class)))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(eventClient);
    }

    @Test
    void updateEvent_whenAuthorized_delegatesToClient() {
        UpdateEventInput input = mock(UpdateEventInput.class);
        when(eventClient.updateEvent("ev-1", input)).thenReturn(EVENT_RESPONSE);

        resolver.updateEvent("ev-1", input);

        verify(eventClient).updateEvent("ev-1", input);
    }

    // ── publishEvent (employee only) ──────────────────────────────────────────

    @Test
    void publishEvent_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.publishEvent("ev-1"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(eventClient);
    }

    @Test
    void publishEvent_whenAuthorized_delegatesToClient() {
        when(eventClient.publishEvent("ev-2")).thenReturn(EVENT_RESPONSE);

        resolver.publishEvent("ev-2");

        verify(eventClient).publishEvent("ev-2");
    }

    // ── cancelEvent (employee only) ───────────────────────────────────────────

    @Test
    void cancelEvent_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.cancelEvent("ev-1"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(eventClient);
    }

    @Test
    void cancelEvent_whenAuthorized_delegatesToClient() {
        when(eventClient.cancelEvent("ev-3")).thenReturn(EVENT_RESPONSE);

        resolver.cancelEvent("ev-3");

        verify(eventClient).cancelEvent("ev-3");
    }
}

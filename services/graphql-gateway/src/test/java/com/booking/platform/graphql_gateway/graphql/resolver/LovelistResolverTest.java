package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.booking.LoveListItem;
import com.booking.platform.common.grpc.booking.LoveListResponse;
import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.graphql_gateway.dto.event.Event;
import com.booking.platform.graphql_gateway.dto.lovelist.LovelistItem;
import com.booking.platform.graphql_gateway.grpc.client.EventClient;
import com.booking.platform.graphql_gateway.grpc.client.LovelistClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LovelistResolverTest {

    @Mock private LovelistClient lovelistClient;
    @Mock private EventClient eventClient;
    @Mock private AuthService authService;

    @InjectMocks private LovelistResolver resolver;

    @BeforeEach
    void auth() {
        lenient().when(authService.getAuthenticatedUserId()).thenReturn("u-1");
    }

    private LoveListResponse oneItem() {
        return LoveListResponse.newBuilder()
                .addItems(LoveListItem.newBuilder()
                        .setEventId("ev-1").setCreatedAt("2025-06-10T12:00:00Z").build())
                .build();
    }

    @Test
    void lovelist_mapsResponse() {
        when(lovelistClient.getLoveList()).thenReturn(oneItem());

        List<LovelistItem> items = resolver.lovelist();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).eventId()).isEqualTo("ev-1");
        assertThat(items.get(0).createdAt()).isEqualTo("2025-06-10T12:00:00Z");
    }

    @Test
    void addFavorite_delegatesToClient() {
        when(lovelistClient.addFavorite("ev-1")).thenReturn(oneItem());

        List<LovelistItem> items = resolver.addFavorite("ev-1");

        verify(lovelistClient).addFavorite("ev-1");
        assertThat(items).hasSize(1);
    }

    @Test
    void removeFavorite_delegatesToClient() {
        when(lovelistClient.removeFavorite("ev-1")).thenReturn(LoveListResponse.getDefaultInstance());

        List<LovelistItem> items = resolver.removeFavorite("ev-1");

        verify(lovelistClient).removeFavorite("ev-1");
        assertThat(items).isEmpty();
    }

    @Test
    void event_hydratesFromEventService() {
        LovelistItem item = new LovelistItem("ev-1", "2025-06-10T12:00:00Z");
        when(eventClient.getEvent("ev-1")).thenReturn(EventResponse.newBuilder()
                .setEvent(EventInfo.newBuilder().setId("ev-1").setTitle("Rock Fest").build())
                .build());

        Event event = resolver.event(item);

        assertThat(event).isNotNull();
        assertThat(event.id()).isEqualTo("ev-1");
    }

    @Test
    void event_whenEventGone_returnsNull() {
        LovelistItem item = new LovelistItem("ev-gone", null);
        when(eventClient.getEvent("ev-gone")).thenThrow(Status.NOT_FOUND.asRuntimeException());

        assertThat(resolver.event(item)).isNull();
    }
}

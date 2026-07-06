package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.booking.BookingInfo;
import com.booking.platform.common.grpc.booking.BookingResponse;
import com.booking.platform.common.grpc.booking.GetUserBookingsResponse;
import com.booking.platform.graphql_gateway.dto.booking.Booking;
import com.booking.platform.graphql_gateway.dto.booking.BookingConnection;
import com.booking.platform.graphql_gateway.dto.booking.CreateBookingInput;
import com.booking.platform.graphql_gateway.grpc.client.BookingClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingResolverTest {

    @Mock private BookingClient bookingClient;
    @Mock private AuthService authService;

    @InjectMocks private BookingResolver resolver;

    private static final BookingResponse BOOKING_RESPONSE = BookingResponse.newBuilder()
            .setBooking(BookingInfo.newBuilder().setId("bk-1").setUserId("u-1").build())
            .build();

    // ── booking query ─────────────────────────────────────────────────────────

    @Test
    void booking_getsAuthenticatedUserId_thenFetchesBooking() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.getBooking("bk-1")).thenReturn(BOOKING_RESPONSE);

        Booking result = resolver.booking("bk-1");

        verify(authService).getAuthenticatedUserId();
        verify(bookingClient).getBooking("bk-1");
        assertThat(result.id()).isEqualTo("bk-1");
    }

    // ── myBookings query ──────────────────────────────────────────────────────

    @Test
    void myBookings_defaultsPageAndPageSize() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.getUserBookings(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(GetUserBookingsResponse.getDefaultInstance());

        resolver.myBookings(null, null, null);

        verify(bookingClient).getUserBookings("u-1", 0, 20, null);
    }

    @Test
    void myBookings_passesExplicitPageAndPageSize() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.getUserBookings(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(GetUserBookingsResponse.getDefaultInstance());

        resolver.myBookings(2, 5, "CONFIRMED");

        verify(bookingClient).getUserBookings("u-1", 2, 5, "CONFIRMED");
    }

    @Test
    void myBookings_usesAuthenticatedUserId() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-99");
        when(bookingClient.getUserBookings(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(GetUserBookingsResponse.getDefaultInstance());

        resolver.myBookings(null, null, null);

        verify(bookingClient).getUserBookings(eq("u-99"), anyInt(), anyInt(), any());
    }

    @Test
    void myBookings_returnsConnection() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.getUserBookings(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(GetUserBookingsResponse.getDefaultInstance());

        BookingConnection conn = resolver.myBookings(null, null, null);

        assertThat(conn).isNotNull();
        assertThat(conn.bookings()).isEmpty();
    }

    // ── createBooking mutation ────────────────────────────────────────────────

    @Test
    void createBooking_usesAuthenticatedUserIdAndDelegatesToClient() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-5");
        CreateBookingInput input = new CreateBookingInput("ev-1", "VIP", 2, "key-123");
        when(bookingClient.createBooking("ev-1", "VIP", 2, "key-123")).thenReturn(BOOKING_RESPONSE);

        Booking result = resolver.createBooking(input);

        verify(authService).getAuthenticatedUserId();
        verify(bookingClient).createBooking("ev-1", "VIP", 2, "key-123");
        assertThat(result.id()).isEqualTo("bk-1");
    }

    // ── cancelBooking mutation ────────────────────────────────────────────────

    @Test
    void cancelBooking_delegatesToClient() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.cancelBooking("bk-5", "User request")).thenReturn(BOOKING_RESPONSE);

        resolver.cancelBooking("bk-5", "User request");

        verify(bookingClient).cancelBooking("bk-5", "User request");
    }

    @Test
    void cancelBooking_requiresAuthentication() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(bookingClient.cancelBooking(any(), any())).thenReturn(BOOKING_RESPONSE);

        resolver.cancelBooking("bk-1", "reason");

        verify(authService).getAuthenticatedUserId();
    }
}

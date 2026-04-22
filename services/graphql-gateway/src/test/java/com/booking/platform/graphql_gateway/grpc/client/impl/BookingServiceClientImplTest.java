package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.booking.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceClientImplTest {

    @Mock private BookingServiceGrpc.BookingServiceBlockingStub stub;

    private BookingServiceClientImpl client;

    private final BookingResponse defaultResponse = BookingResponse.getDefaultInstance();

    @BeforeEach
    void setUp() {
        client = new BookingServiceClientImpl();
        ReflectionTestUtils.setField(client, "bookingServiceStub", stub);
        when(stub.createBooking(any())).thenReturn(defaultResponse);
        when(stub.getBooking(any())).thenReturn(defaultResponse);
        when(stub.getUserBookings(any())).thenReturn(GetUserBookingsResponse.getDefaultInstance());
        when(stub.cancelBooking(any())).thenReturn(defaultResponse);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_setsAllFields() {
        client.createBooking("ev-1", "VIP", 2, "idem-key");

        ArgumentCaptor<CreateBookingRequest> captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(stub).createBooking(captor.capture());
        CreateBookingRequest req = captor.getValue();
        assertThat(req.getEventId()).isEqualTo("ev-1");
        assertThat(req.getSeatCategory()).isEqualTo("VIP");
        assertThat(req.getQuantity()).isEqualTo(2);
        assertThat(req.getIdempotencyKey()).isEqualTo("idem-key");
    }

    @Test
    void createBooking_returnsResponse() {
        BookingResponse result = client.createBooking("ev-1", "Floor", 1, "key");
        assertThat(result).isEqualTo(defaultResponse);
    }

    // ── getBooking ────────────────────────────────────────────────────────────

    @Test
    void getBooking_sendsCorrectBookingId() {
        client.getBooking("bk-1");

        ArgumentCaptor<GetBookingRequest> captor = ArgumentCaptor.forClass(GetBookingRequest.class);
        verify(stub).getBooking(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo("bk-1");
    }

    // ── getUserBookings ───────────────────────────────────────────────────────

    @Test
    void getUserBookings_withStatusFilter_setsFilter() {
        client.getUserBookings("u-1", 0, 10, "CONFIRMED");

        ArgumentCaptor<GetUserBookingsRequest> captor = ArgumentCaptor.forClass(GetUserBookingsRequest.class);
        verify(stub).getUserBookings(captor.capture());
        GetUserBookingsRequest req = captor.getValue();
        assertThat(req.getUserId()).isEqualTo("u-1");
        assertThat(req.getPage()).isEqualTo(0);
        assertThat(req.getPageSize()).isEqualTo(10);
        assertThat(req.getStatusFilter()).isEqualTo("CONFIRMED");
    }

    @Test
    void getUserBookings_nullStatusFilter_doesNotSetFilter() {
        client.getUserBookings("u-1", 0, 10, null);

        ArgumentCaptor<GetUserBookingsRequest> captor = ArgumentCaptor.forClass(GetUserBookingsRequest.class);
        verify(stub).getUserBookings(captor.capture());
        assertThat(captor.getValue().hasStatusFilter()).isFalse();
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_withReason_setsReason() {
        client.cancelBooking("bk-1", "changed mind");

        ArgumentCaptor<CancelBookingRequest> captor = ArgumentCaptor.forClass(CancelBookingRequest.class);
        verify(stub).cancelBooking(captor.capture());
        CancelBookingRequest req = captor.getValue();
        assertThat(req.getBookingId()).isEqualTo("bk-1");
        assertThat(req.getReason()).isEqualTo("changed mind");
    }

    @Test
    void cancelBooking_nullReason_doesNotSetReason() {
        client.cancelBooking("bk-1", null);

        ArgumentCaptor<CancelBookingRequest> captor = ArgumentCaptor.forClass(CancelBookingRequest.class);
        verify(stub).cancelBooking(captor.capture());
        assertThat(captor.getValue().hasReason()).isFalse();
    }
}

package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.ticket.*;
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
class TicketServiceClientImplTest {

    @Mock private TicketServiceGrpc.TicketServiceBlockingStub stub;

    private TicketServiceClientImpl client;

    @BeforeEach
    void setUp() {
        client = new TicketServiceClientImpl();
        ReflectionTestUtils.setField(client, "ticketServiceStub", stub);
        when(stub.getMyTickets(any())).thenReturn(GetMyTicketsResponse.getDefaultInstance());
        when(stub.getTicketsByBooking(any())).thenReturn(GetTicketsByBookingResponse.getDefaultInstance());
        when(stub.getTicketsByUser(any())).thenReturn(GetTicketsByUserResponse.getDefaultInstance());
        when(stub.getTicketByNumber(any())).thenReturn(TicketResponse.getDefaultInstance());
        when(stub.validateTicket(any())).thenReturn(TicketResponse.getDefaultInstance());
        when(stub.cancelTicket(any())).thenReturn(TicketResponse.getDefaultInstance());
    }

    // ── getMyTickets ──────────────────────────────────────────────────────────

    @Test
    void getMyTickets_sendsPageAndSize() {
        client.getMyTickets(2, 15);

        ArgumentCaptor<GetMyTicketsRequest> captor = ArgumentCaptor.forClass(GetMyTicketsRequest.class);
        verify(stub).getMyTickets(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(15);
    }

    // ── getTicketsByBooking ───────────────────────────────────────────────────

    @Test
    void getTicketsByBooking_sendsBookingId() {
        client.getTicketsByBooking("bk-1");

        ArgumentCaptor<GetTicketsByBookingRequest> captor = ArgumentCaptor.forClass(GetTicketsByBookingRequest.class);
        verify(stub).getTicketsByBooking(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo("bk-1");
    }

    // ── getTicketsByUser ──────────────────────────────────────────────────────

    @Test
    void getTicketsByUser_sendsAllFields() {
        client.getTicketsByUser("u-1", 0, 20);

        ArgumentCaptor<GetTicketsByUserRequest> captor = ArgumentCaptor.forClass(GetTicketsByUserRequest.class);
        verify(stub).getTicketsByUser(captor.capture());
        GetTicketsByUserRequest req = captor.getValue();
        assertThat(req.getUserId()).isEqualTo("u-1");
        assertThat(req.getPage()).isEqualTo(0);
        assertThat(req.getPageSize()).isEqualTo(20);
    }

    // ── getTicketByNumber ─────────────────────────────────────────────────────

    @Test
    void getTicketByNumber_sendsTicketNumber() {
        client.getTicketByNumber("TKT-001");

        ArgumentCaptor<GetTicketByNumberRequest> captor = ArgumentCaptor.forClass(GetTicketByNumberRequest.class);
        verify(stub).getTicketByNumber(captor.capture());
        assertThat(captor.getValue().getTicketNumber()).isEqualTo("TKT-001");
    }

    // ── validateTicket ────────────────────────────────────────────────────────

    @Test
    void validateTicket_sendsTicketNumber() {
        client.validateTicket("TKT-002");

        ArgumentCaptor<ValidateTicketRequest> captor = ArgumentCaptor.forClass(ValidateTicketRequest.class);
        verify(stub).validateTicket(captor.capture());
        assertThat(captor.getValue().getTicketNumber()).isEqualTo("TKT-002");
    }

    // ── cancelTicket ──────────────────────────────────────────────────────────

    @Test
    void cancelTicket_sendsTicketNumber() {
        client.cancelTicket("TKT-003");

        ArgumentCaptor<CancelTicketRequest> captor = ArgumentCaptor.forClass(CancelTicketRequest.class);
        verify(stub).cancelTicket(captor.capture());
        assertThat(captor.getValue().getTicketNumber()).isEqualTo("TKT-003");
    }
}

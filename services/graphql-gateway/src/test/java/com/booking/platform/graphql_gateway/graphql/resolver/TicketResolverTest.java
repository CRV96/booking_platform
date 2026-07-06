package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.ticket.GetMyTicketsResponse;
import com.booking.platform.common.grpc.ticket.GetTicketsByBookingResponse;
import com.booking.platform.common.grpc.ticket.GetTicketsByUserResponse;
import com.booking.platform.common.grpc.ticket.TicketInfo;
import com.booking.platform.common.grpc.ticket.TicketResponse;
import com.booking.platform.graphql_gateway.dto.ticket.Ticket;
import com.booking.platform.graphql_gateway.dto.ticket.TicketConnection;
import com.booking.platform.graphql_gateway.exception.ErrorCode;
import com.booking.platform.graphql_gateway.exception.GraphQLException;
import com.booking.platform.graphql_gateway.grpc.client.TicketClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketResolverTest {

    @Mock private TicketClient ticketClient;
    @Mock private AuthService authService;

    @InjectMocks private TicketResolver resolver;

    private static final TicketResponse TICKET_RESPONSE = TicketResponse.newBuilder()
            .setTicket(TicketInfo.newBuilder()
                    .setId("tkt-1").setTicketNumber("TKT-0001").setStatus("VALID")
                    .build())
            .build();

    // ── myTickets (authenticated customer) ───────────────────────────────────

    @Test
    void myTickets_requiresAuthentication() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(ticketClient.getMyTickets(anyInt(), anyInt()))
                .thenReturn(GetMyTicketsResponse.getDefaultInstance());

        resolver.myTickets(null, null);

        verify(authService).getAuthenticatedUserId();
    }

    @Test
    void myTickets_defaultsPageAndPageSize() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(ticketClient.getMyTickets(anyInt(), anyInt()))
                .thenReturn(GetMyTicketsResponse.getDefaultInstance());

        resolver.myTickets(null, null);

        verify(ticketClient).getMyTickets(0, 20);
    }

    @Test
    void myTickets_passesExplicitPaging() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(ticketClient.getMyTickets(anyInt(), anyInt()))
                .thenReturn(GetMyTicketsResponse.getDefaultInstance());

        resolver.myTickets(3, 15);

        verify(ticketClient).getMyTickets(3, 15);
    }

    @Test
    void myTickets_returnsConnection() {
        when(authService.getAuthenticatedUserId()).thenReturn("u-1");
        when(ticketClient.getMyTickets(anyInt(), anyInt()))
                .thenReturn(GetMyTicketsResponse.getDefaultInstance());

        TicketConnection conn = resolver.myTickets(null, null);

        assertThat(conn).isNotNull();
        assertThat(conn.tickets()).isEmpty();
    }

    // ── ticketsByBooking (employee only) ─────────────────────────────────────

    @Test
    void ticketsByBooking_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.ticketsByBooking("bk-1"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(ticketClient);
    }

    @Test
    void ticketsByBooking_whenAuthorized_delegatesToClient() {
        TicketInfo ticket = TicketInfo.newBuilder().setId("tkt-2").setTicketNumber("TKT-0002").build();
        GetTicketsByBookingResponse response = GetTicketsByBookingResponse.newBuilder()
                .addTickets(ticket).build();
        when(ticketClient.getTicketsByBooking("bk-1")).thenReturn(response);

        List<Ticket> tickets = resolver.ticketsByBooking("bk-1");

        verify(authService).requireRole("employee");
        verify(ticketClient).getTicketsByBooking("bk-1");
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).id()).isEqualTo("tkt-2");
    }

    // ── ticketsByUser (employee only) ─────────────────────────────────────────

    @Test
    void ticketsByUser_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.ticketsByUser("u-1", null, null))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(ticketClient);
    }

    @Test
    void ticketsByUser_defaultsPaging() {
        when(ticketClient.getTicketsByUser(anyString(), anyInt(), anyInt()))
                .thenReturn(GetTicketsByUserResponse.getDefaultInstance());

        resolver.ticketsByUser("u-1", null, null);

        verify(ticketClient).getTicketsByUser("u-1", 0, 20);
    }

    // ── ticket (employee only) ────────────────────────────────────────────────

    @Test
    void ticket_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.ticket("TKT-0001"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(ticketClient);
    }

    @Test
    void ticket_whenAuthorized_delegatesToClient() {
        when(ticketClient.getTicketByNumber("TKT-0001")).thenReturn(TICKET_RESPONSE);

        Ticket result = resolver.ticket("TKT-0001");

        verify(ticketClient).getTicketByNumber("TKT-0001");
        assertThat(result.ticketNumber()).isEqualTo("TKT-0001");
    }

    // ── validateTicket (employee only) ────────────────────────────────────────

    @Test
    void validateTicket_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.validateTicket("TKT-0001"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(ticketClient);
    }

    @Test
    void validateTicket_whenAuthorized_delegatesToClient() {
        when(ticketClient.validateTicket("TKT-0001")).thenReturn(TICKET_RESPONSE);

        Ticket result = resolver.validateTicket("TKT-0001");

        verify(authService).requireRole("employee");
        verify(ticketClient).validateTicket("TKT-0001");
        assertThat(result.status()).isEqualTo("VALID");
    }

    // ── cancelTicket (employee only) ──────────────────────────────────────────

    @Test
    void cancelTicket_requiresEmployeeRole() {
        doThrow(new GraphQLException(ErrorCode.FORBIDDEN))
                .when(authService).requireRole("employee");

        assertThatThrownBy(() -> resolver.cancelTicket("TKT-0001"))
                .isInstanceOf(GraphQLException.class);

        verifyNoInteractions(ticketClient);
    }

    @Test
    void cancelTicket_whenAuthorized_delegatesToClient() {
        when(ticketClient.cancelTicket("TKT-0002")).thenReturn(TICKET_RESPONSE);

        resolver.cancelTicket("TKT-0002");

        verify(ticketClient).cancelTicket("TKT-0002");
    }
}

package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.ticket.GetMyTicketsResponse;
import com.booking.platform.common.grpc.ticket.GetTicketsByBookingResponse;
import com.booking.platform.common.grpc.ticket.GetTicketsByUserResponse;
import com.booking.platform.common.grpc.ticket.TicketResponse;

/**
 * Client interface for communicating with the ticket-service via gRPC.
 */
public interface TicketClient {

    GetMyTicketsResponse getMyTickets(int page, int pageSize);

    GetTicketsByBookingResponse getTicketsByBooking(String bookingId);

    GetTicketsByUserResponse getTicketsByUser(String userId, int page, int pageSize);

    TicketResponse getTicketByNumber(String ticketNumber);

    TicketResponse validateTicket(String ticketNumber);

    TicketResponse cancelTicket(String ticketNumber);
}

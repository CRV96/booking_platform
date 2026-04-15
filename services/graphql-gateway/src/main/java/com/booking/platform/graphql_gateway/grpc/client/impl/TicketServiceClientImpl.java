package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.ticket.*;
import com.booking.platform.graphql_gateway.constants.TicketServiceConst;
import com.booking.platform.graphql_gateway.grpc.client.TicketClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * gRPC client implementation for calling ticket-service.
 * JWT is forwarded automatically by {@code JwtForwardingClientInterceptor}.
 */
@Service
@Slf4j
public class TicketServiceClientImpl implements TicketClient {

    @GrpcClient(TicketServiceConst.GRPC_CLIENT)
    private TicketServiceGrpc.TicketServiceBlockingStub ticketServiceStub;

    @Override
    public GetMyTicketsResponse getMyTickets(int page, int pageSize) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: GetMyTickets page={}, size={}", page, pageSize);

        return ticketServiceStub.getMyTickets(
                GetMyTicketsRequest.newBuilder()
                        .setPage(page)
                        .setPageSize(pageSize)
                        .build()
        );
    }

    @Override
    public GetTicketsByBookingResponse getTicketsByBooking(String bookingId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: GetTicketsByBooking bookingId='{}'", bookingId);

        return ticketServiceStub.getTicketsByBooking(
                GetTicketsByBookingRequest.newBuilder()
                        .setBookingId(bookingId)
                        .build()
        );
    }

    @Override
    public GetTicketsByUserResponse getTicketsByUser(String userId, int page, int pageSize) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: GetTicketsByUser userId='{}', page={}, size={}", userId, page, pageSize);

        return ticketServiceStub.getTicketsByUser(
                GetTicketsByUserRequest.newBuilder()
                        .setUserId(userId)
                        .setPage(page)
                        .setPageSize(pageSize)
                        .build()
        );
    }

    @Override
    public TicketResponse getTicketByNumber(String ticketNumber) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: GetTicketByNumber ticketNumber='{}'", ticketNumber);

        return ticketServiceStub.getTicketByNumber(
                GetTicketByNumberRequest.newBuilder()
                        .setTicketNumber(ticketNumber)
                        .build()
        );
    }

    @Override
    public TicketResponse validateTicket(String ticketNumber) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: ValidateTicket ticketNumber='{}'", ticketNumber);

        return ticketServiceStub.validateTicket(
                ValidateTicketRequest.newBuilder()
                        .setTicketNumber(ticketNumber)
                        .build()
        );
    }

    @Override
    public TicketResponse cancelTicket(String ticketNumber) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling ticket-service: CancelTicket ticketNumber='{}'", ticketNumber);

        return ticketServiceStub.cancelTicket(
                CancelTicketRequest.newBuilder()
                        .setTicketNumber(ticketNumber)
                        .build()
        );
    }
}

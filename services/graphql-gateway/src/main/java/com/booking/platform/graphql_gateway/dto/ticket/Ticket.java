package com.booking.platform.graphql_gateway.dto.ticket;

import com.booking.platform.common.grpc.ticket.TicketInfo;

/**
 * GraphQL DTO representing a ticket.
 * Maps from the gRPC TicketInfo protobuf message.
 */
public record Ticket(
        String id,
        String bookingId,
        String eventId,
        String userId,
        String ticketNumber,
        String qrCodeData,
        String seatCategory,
        String seatNumber,
        String status,
        String eventTitle,
        String createdAt
) {
    /**
     * Maps a gRPC TicketInfo to a GraphQL Ticket DTO.
     */
    public static Ticket fromGrpc(TicketInfo info) {
        return new Ticket(
                info.getId(),
                info.getBookingId(),
                info.getEventId(),
                info.getUserId(),
                info.getTicketNumber(),
                info.getQrCodeData(),
                info.getSeatCategory(),
                info.getSeatNumber().isBlank() ? null : info.getSeatNumber(),
                info.getStatus(),
                info.getEventTitle(),
                info.getCreatedAt().isBlank() ? null : info.getCreatedAt()
        );
    }
}

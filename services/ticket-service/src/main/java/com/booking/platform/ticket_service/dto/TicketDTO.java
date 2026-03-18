package com.booking.platform.ticket_service.dto;

import lombok.Builder;

/**
 * Data Transfer Object for Ticket information.
 */
@Builder
public record TicketDTO
        (
                String bookingId,
                String eventId,
                String userId,
                String seatCategory,
                int quantity,
                String eventTitle
        )
{}

package com.booking.platform.ticket_service.dto;

import lombok.Builder;

/**
 * Data Transfer Object carrying the information needed to generate tickets for a confirmed booking.
 *
 * @param bookingId    unique booking identifier
 * @param eventId      event identifier from event-service
 * @param userId       Keycloak user ID of the ticket holder
 * @param seatCategory seat category (e.g. "VIP", "General Admission")
 * @param quantity     number of tickets to generate (1..maxQuantityPerBooking)
 * @param eventTitle   event title, denormalized for ticket display
 */
@Builder
public record TicketDTO(
        String bookingId,
        String eventId,
        String userId,
        String seatCategory,
        int quantity,
        String eventTitle
) {}

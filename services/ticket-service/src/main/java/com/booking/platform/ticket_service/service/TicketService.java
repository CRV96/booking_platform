package com.booking.platform.ticket_service.service;

import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.dto.TicketDTO;

import java.util.List;

/**
 * Business logic for ticket operations.
 */
public interface TicketService {

    /**
     * Generates {@code quantity} tickets for a confirmed booking.
     * Each ticket gets a unique ticket number and QR code data.
     *
     * @param bookingId    UUID of the confirmed booking
     * @param eventId      event ID from event-service
     * @param userId       Keycloak user ID of the ticket holder
     * @param seatCategory seat category name (e.g. "VIP")
     * @param quantity     number of tickets to generate
     * @param eventTitle   event title for display on tickets
     * @return list of generated tickets
     */
    List<TicketDocument> generateTickets(TicketDTO ticket);

    /**
     * Retrieves all tickets for a given booking.
     */
    List<TicketDocument> getTicketsByBooking(String bookingId);

    TicketDocument getByTicketNumber(String ticketNumber);

    List<TicketDocument> getTicketsByUserId(String userId);

    TicketDocument validateTicket(String ticketNumber);

    TicketDocument cancelTicket(String ticketNumber);
}

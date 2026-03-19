package com.booking.platform.ticket_service.service;

import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.dto.TicketDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business logic for ticket operations.
 */
public interface TicketService {

    /**
     * Generates tickets for a confirmed booking.
     * Each ticket gets a unique ticket number and QR code data.
     */
    List<TicketDocument> generateTickets(TicketDTO ticket);

    /**
     * Retrieves all tickets for a given booking.
     */
    List<TicketDocument> getTicketsByBooking(String bookingId);

    /** Retrieves a ticket by its unique ticket number. */
    TicketDocument getByTicketNumber(String ticketNumber);

    /** Retrieves all tickets for a given user. */
    List<TicketDocument> getTicketsByUserId(String userId);

    /** Retrieves tickets for a given user with pagination. */
    Page<TicketDocument> getTicketsByUserId(String userId, Pageable pageable);

    /** Validates a ticket and marks it as USED. Throws if already used or cancelled. */
    TicketDocument validateTicket(String ticketNumber);

    /** Cancels a ticket and marks it as CANCELLED. Throws if already used. */
    TicketDocument cancelTicket(String ticketNumber);
}

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

    TicketDocument getByTicketNumber(String ticketNumber);

    List<TicketDocument> getTicketsByUserId(String userId);

    Page<TicketDocument> getTicketsByUserId(String userId, Pageable pageable);

    TicketDocument validateTicket(String ticketNumber);

    TicketDocument cancelTicket(String ticketNumber);
}

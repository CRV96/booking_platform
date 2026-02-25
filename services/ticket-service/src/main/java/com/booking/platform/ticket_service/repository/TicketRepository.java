package com.booking.platform.ticket_service.repository;

import com.booking.platform.ticket_service.document.TicketDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link TicketDocument}.
 */
@Repository
public interface TicketRepository extends MongoRepository<TicketDocument, String> {

    /**
     * Finds all tickets belonging to a specific booking.
     */
    List<TicketDocument> findByBookingId(String bookingId);

    /**
     * Finds a ticket by its human-readable ticket number (e.g. {@code TKT-20240215-A1B2C3}).
     */
    Optional<TicketDocument> findByTicketNumber(String ticketNumber);
}

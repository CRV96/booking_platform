package com.booking.platform.ticket_service.service.impl;

import com.booking.platform.ticket_service.constants.TicketConstants;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.enums.TicketStatus;
import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.exception.TicketAlreadyUsedException;
import com.booking.platform.ticket_service.exception.TicketCancelledException;
import com.booking.platform.ticket_service.exception.TicketNotFoundException;
import com.booking.platform.ticket_service.repository.TicketRepository;
import com.booking.platform.ticket_service.service.TicketService;
import com.booking.platform.ticket_service.validation.BookingValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link TicketService}.
 *
 * <p>Generates tickets with:
 * <ul>
 *   <li><b>Ticket number</b>: {@code TKT-yyyyMMdd-XXXXXX} — human-readable, unique</li>
 *   <li><b>QR code data</b>: UUID v4 — machine-scannable, unique</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(TicketConstants.TICKET_NUMBER_DATE_FORMAT);

    private final TicketRepository ticketRepository;
    private final BookingValidation bookingValidation;

    /**
     * Generates tickets for a confirmed booking.
     * @param source DTO containing booking and ticket details
     * @return list of generated (or existing) tickets for the booking
     */
    @Override
    public List<TicketDocument> generateTickets(TicketDTO source) {
        bookingValidation.validateTicketRequest(source);

        // Idempotency: if tickets already exist for this booking, return them
        List<TicketDocument> existing = ticketRepository.findByBookingId(source.bookingId());
        if (!existing.isEmpty()) {
            log.info("Tickets already exist for booking '{}', returning {} existing tickets",
                    source.bookingId(), existing.size());
            return existing;
        }

        List<TicketDocument> saved = ticketRepository.saveAll(generateTicketsForBooking(source));

        log.debug("Generated {} tickets for booking '{}': {}",
                saved.size(), source.bookingId(),
                saved.stream().map(TicketDocument::getTicketNumber).toList());

        return saved;
    }

    /** Retrieves all tickets associated with a specific booking ID. */
    @Override
    public List<TicketDocument> getTicketsByBooking(String bookingId) {
        bookingValidation.validateBookingId(bookingId);

        final List<TicketDocument> ticketDocuments = ticketRepository.findByBookingId(bookingId);
        log.debug("Retrieved {} tickets for booking '{}'", ticketDocuments.size(), bookingId);

        return ticketDocuments;
    }

    /** Retrieves a ticket by its unique ticket number. */
    @Override
    public TicketDocument getByTicketNumber(String ticketNumber) {
        log.debug("Retrieving ticket by ticket number '{}'", ticketNumber);
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException(ticketNumber));
    }

    /** Retrieves all tickets associated with a specific user ID. */
    @Override
    public List<TicketDocument> getTicketsByUserId(String userId) {
        final List<TicketDocument> ticketDocuments = ticketRepository.findByUserId(userId);
        log.debug("Retrieved {} tickets for user '{}'", ticketDocuments.size(), userId);

        return ticketDocuments;
    }

    /** Retrieves tickets for a specific user with pagination. */
    @Override
    public Page<TicketDocument> getTicketsByUserId(String userId, Pageable pageable) {
        Page<TicketDocument> ticketPage = ticketRepository.findByUserId(userId, pageable);
        log.debug("Retrieved {} tickets (page {}) for user '{}'",
                ticketPage.getNumberOfElements(), pageable.getPageNumber(), userId);
        return ticketPage;
    }

    /** Validates a ticket by its ticket number. Marks the ticket as USED if valid. */
    @Override
    public TicketDocument validateTicket(String ticketNumber) {
        bookingValidation.validateTicketNumber(ticketNumber);

        TicketDocument ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException(ticketNumber));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw new TicketAlreadyUsedException(ticketNumber);
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new TicketCancelledException(ticketNumber);
        }

        ticket.setStatus(TicketStatus.USED);
        TicketDocument saved = ticketRepository.save(ticket);
        log.info("Ticket '{}' validated — marked as USED", ticketNumber);
        return saved;
    }

    /** Cancels a ticket by its ticket number. Marks the ticket as CANCELLED if valid. */
    @Override
    public TicketDocument cancelTicket(String ticketNumber) {
        bookingValidation.validateTicketNumber(ticketNumber);

        TicketDocument ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new TicketNotFoundException(ticketNumber));

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            log.debug("Ticket '{}' is already cancelled", ticketNumber);
            return ticket;
        }
        if (ticket.getStatus() == TicketStatus.USED) {
            throw new TicketAlreadyUsedException(ticketNumber);
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        TicketDocument saved = ticketRepository.save(ticket);
        log.info("Ticket '{}' cancelled", ticketNumber);
        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Generates a unique ticket number in format: {@code TKT-yyyyMMdd-XXXXXX}.
     * The 6-character suffix is derived from a UUID for uniqueness.
     */
    private String generateTicketNumber(String datePrefix) {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, TicketConstants.TICKET_NUMBER_SUFFIX_LENGTH)
                .toUpperCase();

        log.debug("Generated ticket number suffix: '{}'", suffix);

        return TicketConstants.TICKET_NUMBER_PREFIX + "-" + datePrefix + "-" + suffix;
    }

    /**
     * Generates a list of TicketDocument objects for a given booking.
     * @return list of generated TicketDocument objects (not yet saved to DB)
     */
    private List<TicketDocument> generateTicketsForBooking(TicketDTO source) {
        List<TicketDocument> tickets = new ArrayList<>(source.quantity());
        String datePrefix = LocalDate.now().format(DATE_FORMAT);

        log.debug("Generating {} tickets for booking '{}', event '{}', user '{}'",
                source.quantity(), source.bookingId(), source.eventId(), source.userId());

        for (int i = 0; i < source.quantity(); i++) {
            String ticketNumber = generateTicketNumber(datePrefix);
            String qrCodeData = UUID.randomUUID().toString();

            TicketDocument ticket = TicketDocument.builder()
                    .bookingId(source.bookingId())
                    .eventId(source.eventId())
                    .userId(source.userId())
                    .ticketNumber(ticketNumber)
                    .qrCodeData(qrCodeData)
                    .seatCategory(source.seatCategory())
                    .status(TicketStatus.VALID)
                    .eventTitle(source.eventTitle())
                    .build();

            tickets.add(ticket);
        }

        log.debug("Generated ticket numbers for booking '{}': {}",
                source.bookingId(), tickets.stream().map(TicketDocument::getTicketNumber).toList());

        return tickets;
    }

}

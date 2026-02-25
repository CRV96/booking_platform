package com.booking.platform.ticket_service.service;

import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.TicketStatus;
import com.booking.platform.ticket_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TicketRepository ticketRepository;

    @Override
    public List<TicketDocument> generateTickets(String bookingId, String eventId, String userId,
                                                 String seatCategory, int quantity, String eventTitle) {
        // Idempotency: if tickets already exist for this booking, return them
        List<TicketDocument> existing = ticketRepository.findByBookingId(bookingId);
        if (!existing.isEmpty()) {
            log.info("Tickets already exist for booking '{}', returning {} existing tickets",
                    bookingId, existing.size());
            return existing;
        }

        List<TicketDocument> tickets = new ArrayList<>(quantity);
        String datePrefix = LocalDate.now().format(DATE_FORMAT);

        for (int i = 0; i < quantity; i++) {
            String ticketNumber = generateTicketNumber(datePrefix);
            String qrCodeData = UUID.randomUUID().toString();

            TicketDocument ticket = TicketDocument.builder()
                    .bookingId(bookingId)
                    .eventId(eventId)
                    .userId(userId)
                    .ticketNumber(ticketNumber)
                    .qrCodeData(qrCodeData)
                    .seatCategory(seatCategory)
                    .status(TicketStatus.VALID)
                    .eventTitle(eventTitle)
                    .build();

            tickets.add(ticket);
        }

        List<TicketDocument> saved = ticketRepository.saveAll(tickets);
        log.info("Generated {} tickets for booking '{}': {}",
                saved.size(), bookingId,
                saved.stream().map(TicketDocument::getTicketNumber).toList());

        return saved;
    }

    @Override
    public List<TicketDocument> getTicketsByBooking(String bookingId) {
        return ticketRepository.findByBookingId(bookingId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Generates a unique ticket number in format: {@code TKT-yyyyMMdd-XXXXXX}.
     * The 6-character suffix is derived from a UUID for uniqueness.
     */
    private String generateTicketNumber(String datePrefix) {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "TKT-" + datePrefix + "-" + suffix;
    }
}

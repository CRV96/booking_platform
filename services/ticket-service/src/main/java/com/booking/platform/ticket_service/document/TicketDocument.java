package com.booking.platform.ticket_service.document;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document representing a single ticket.
 *
 * <p>Tickets are generated in batches when a booking is confirmed. Each ticket
 * has a unique human-readable {@link #ticketNumber} (for display) and a unique
 * {@link #qrCodeData} UUID (for scanning at the venue).</p>
 *
 * <p>Multiple tickets can belong to the same booking (e.g. 3 VIP seats → 3 tickets).
 * The {@link #bookingId} field is indexed for efficient lookup by booking.</p>
 */
@Document(collection = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDocument {

    @Id
    private String id;

    /** Booking UUID that this ticket belongs to. */
    @Indexed
    private String bookingId;

    /** Event ID from event-service. */
    private String eventId;

    /** Keycloak user ID of the ticket holder. */
    private String userId;

    /**
     * Human-readable ticket number.
     * Format: {@code TKT-yyyyMMdd-XXXXXX} where X is alphanumeric.
     */
    @Indexed(unique = true)
    private String ticketNumber;

    /**
     * UUID-based unique data embedded in the QR code.
     * Used for scanning at the venue to validate entry.
     */
    @Indexed(unique = true)
    private String qrCodeData;

    /** Seat category (e.g. "VIP", "General Admission"). */
    private String seatCategory;

    /** Assigned seat number (nullable — not all events have assigned seating). */
    private String seatNumber;

    /** Current lifecycle status of the ticket. */
    private TicketStatus status;

    /** Event title, denormalized for ticket display. */
    private String eventTitle;

    /** Timestamp when the ticket was created. */
    @CreatedDate
    private Instant createdAt;
}

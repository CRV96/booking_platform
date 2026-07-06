package com.booking.platform.ticket_service.document.enums;

/**
 * Lifecycle status of a ticket.
 *
 * <ul>
 *   <li>{@link #VALID}     — ticket is active and can be used for entry</li>
 *   <li>{@link #USED}      — ticket has been scanned at the venue</li>
 *   <li>{@link #CANCELLED} — ticket was cancelled (booking cancelled / refunded)</li>
 *   <li>{@link #EXPIRED}   — event date has passed without the ticket being used</li>
 * </ul>
 */
public enum TicketStatus {
    VALID,
    USED,
    CANCELLED,
    EXPIRED
}

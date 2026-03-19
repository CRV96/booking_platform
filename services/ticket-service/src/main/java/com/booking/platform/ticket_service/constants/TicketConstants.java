package com.booking.platform.ticket_service.constants;

/**
 * Centralized class for all constants used in the Ticket Service.
 */
public final class TicketConstants {

    private TicketConstants() {}

    /** Prefix for human-readable ticket numbers (e.g. {@code TKT-20240215-A1B2C3}). */
    public static final String TICKET_NUMBER_PREFIX = "TKT";

    /** Date format used in the ticket number (e.g. {@code 20240215}). */
    public static final String TICKET_NUMBER_DATE_FORMAT = "yyyyMMdd";

    /** Length of the random alphanumeric suffix in the ticket number. */
    public static final int TICKET_NUMBER_SUFFIX_LENGTH = 6;

    /** MongoDB collection name for ticket documents. */
    public static final String COLLECTION_NAME = "tickets";
}

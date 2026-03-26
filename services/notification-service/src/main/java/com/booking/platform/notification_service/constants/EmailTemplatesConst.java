package com.booking.platform.notification_service.constants;

import com.booking.platform.notification_service.email.EmailService;

/**
 * Central registry of all email template names, subject lines, and
 * Thymeleaf variable keys used across notification-service.
 *
 * <p>Each nested class corresponds to one HTML template file in
 * {@code classpath:/templates/}. It holds:
 * <ul>
 *   <li>{@code TEMPLATE} — the template file name (without {@code .html}),
 *       passed directly to {@link EmailService#sendHtml}</li>
 *   <li>{@code SUBJECT}  — the email subject line for that template</li>
 *   <li>{@code Vars}     — string constants for every {@code ${variable}}
 *       referenced inside that template, used as keys in the
 *       {@code Map<String, Object>} variables map</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>
 * emailService.sendHtml(
 *     recipientEmail,
 *     EmailTemplates.BookingConfirmation.SUBJECT,
 *     EmailTemplates.BookingConfirmation.TEMPLATE,
 *     Map.of(
 *         EmailTemplates.BookingConfirmation.Vars.BOOKING_ID, event.getBookingId(),
 *         EmailTemplates.BookingConfirmation.Vars.EVENT_ID,   event.getEventId()
 *     )
 * );
 * </pre>
 */
public final class EmailTemplatesConst {

    private EmailTemplatesConst() {
        // Utility class — not instantiable
    }

    // ── Booking Confirmation ──────────────────────────────────────────────────

    /** Sent when a booking transitions to CONFIRMED (payment succeeded). */
    public static final class BookingConfirmation {
        public static final String TEMPLATE = "booking-confirmation";
        public static final String SUBJECT  = "Your Booking is Confirmed! \uD83C\uDF89";

        public static final class Vars {
            public static final String BOOKING_ID    = "bookingId";
            public static final String EVENT_ID      = "eventId";
            public static final String TICKET_IDS    = "ticketIds";
            public static final String SEAT_CATEGORY = "seatCategory";
            public static final String QUANTITY      = "quantity";
            public static final String TOTAL_PRICE   = "totalPrice";
            public static final String CURRENCY      = "currency";
            public static final String TIMESTAMP     = "timestamp";

            private Vars() {}
        }

        private BookingConfirmation() {}
    }

    // ── Booking Cancellation ──────────────────────────────────────────────────

    /** Sent when a booking is cancelled (by user or system). */
    public static final class BookingCancellation {
        public static final String TEMPLATE = "booking-cancellation";
        public static final String SUBJECT  = "Your Booking Has Been Cancelled";

        public static final class Vars {
            public static final String BOOKING_ID = "bookingId";
            public static final String EVENT_ID   = "eventId";
            public static final String REASON     = "reason";
            public static final String TIMESTAMP  = "timestamp";

            private Vars() {}
        }

        private BookingCancellation() {}
    }

    // ── Event Cancelled ───────────────────────────────────────────────────────

    /** Sent to attendees when an organiser cancels an event. */
    public static final class EventCancelled {
        public static final String TEMPLATE = "event-cancelled";
        public static final String SUBJECT  = "Important: Event Cancelled";

        public static final class Vars {
            public static final String EVENT_ID  = "eventId";
            public static final String REASON    = "reason";
            public static final String TIMESTAMP = "timestamp";

            private Vars() {}
        }

        private EventCancelled() {}
    }

    // ── Event Reminder ────────────────────────────────────────────────────────

    /** Sent to attendees 24 hours before their booked event. */
    public static final class EventReminder {
        public static final String TEMPLATE = "event-reminder";
        public static final String SUBJECT  = "Reminder: Your Event is Tomorrow!";

        public static final class Vars {
            public static final String TITLE      = "title";
            public static final String CATEGORY   = "category";
            public static final String DATE_TIME  = "dateTime";
            public static final String VENUE_NAME = "venueName";
            public static final String VENUE_CITY = "venueCity";
            public static final String BOOKING_ID = "bookingId";

            private Vars() {}
        }

        private EventReminder() {}
    }

    // ── Event Created ──────────────────────────────────────────────────────────

    /** Sent to the organizer when a new event draft is created. */
    public static final class EventCreated {
        public static final String TEMPLATE = "event-created";
        public static final String SUBJECT  = "Your Event Draft Has Been Created";

        public static final class Vars {
            public static final String EVENT_ID      = "eventId";
            public static final String TITLE         = "title";
            public static final String CATEGORY      = "category";
            public static final String VENUE_NAME    = "venueName";
            public static final String VENUE_CITY    = "venueCity";
            public static final String VENUE_COUNTRY = "venueCountry";
            public static final String TIMESTAMP     = "timestamp";

            private Vars() {}
        }

        private EventCreated() {}
    }

    // ── Event Published ────────────────────────────────────────────────────────

    /** Sent to the organizer when their event goes live. */
    public static final class EventPublished {
        public static final String TEMPLATE = "event-published";
        public static final String SUBJECT  = "Your Event is Now Live!";

        public static final class Vars {
            public static final String EVENT_ID  = "eventId";
            public static final String TITLE     = "title";
            public static final String CATEGORY  = "category";
            public static final String DATE_TIME = "dateTime";
            public static final String TIMESTAMP = "timestamp";

            private Vars() {}
        }

        private EventPublished() {}
    }
}

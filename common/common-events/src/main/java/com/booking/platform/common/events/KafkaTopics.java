package com.booking.platform.common.events;

/**
 * Central registry of all Kafka topic names used across the platform.
 *
 * <p>Every service that publishes or consumes Kafka messages must reference these
 * constants rather than hard-coding topic strings. This guarantees that producer
 * and consumer always agree on the topic name and makes topic renaming a
 * single-place change.
 *
 * <p>Naming convention: {@code <domain>.<entity>.<past-tense-action>}
 *
 * <p>Topic-to-service mapping:
 * <pre>
 * Producer              Topic                          Consumers
 * ─────────────────────────────────────────────────────────────────────────────
 * event-service    →  events.event.created        →  notification, analytics
 * event-service    →  events.event.updated        →  analytics
 * event-service    →  events.event.published      →  notification, analytics
 * event-service    →  events.event.cancelled      →  notification, analytics
 *
 * booking-service  →  events.booking.created      →  payment
 * booking-service  →  events.booking.confirmed    →  ticket, notification, analytics
 * booking-service  →  events.booking.cancelled    →  event (seat release), notification
 *
 * payment-service  →  events.payment.completed         →  booking
 * payment-service  →  events.payment.failed            →  booking, notification
 * payment-service  →  events.payment.refund-completed  →  booking
 * </pre>
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class — not instantiable
    }

    // ── Event lifecycle ───────────────────────────────────────────────────────

    /** Published when a new event is created in DRAFT status. */
    public static final String EVENT_CREATED   = "events.event.created";

    /** Published when an existing event's fields are modified. */
    public static final String EVENT_UPDATED   = "events.event.updated";

    /** Published when an event transitions from DRAFT to PUBLISHED. */
    public static final String EVENT_PUBLISHED = "events.event.published";

    /** Published when an event is cancelled (from DRAFT or PUBLISHED). */
    public static final String EVENT_CANCELLED = "events.event.cancelled";

    // ── Booking lifecycle ─────────────────────────────────────────────────────

    /** Published when a user initiates a booking (status: PENDING). */
    public static final String BOOKING_CREATED   = "events.booking.created";

    /** Published when a booking is confirmed after successful payment. */
    public static final String BOOKING_CONFIRMED = "events.booking.confirmed";

    /** Published when a booking is cancelled by the user or by the system. */
    public static final String BOOKING_CANCELLED = "events.booking.cancelled";

    // ── Payment lifecycle ─────────────────────────────────────────────────────

    /** Published when a payment charge succeeds. */
    public static final String PAYMENT_COMPLETED = "events.payment.completed";

    /** Published when a payment charge fails (card declined, timeout, etc.). */
    public static final String PAYMENT_FAILED    = "events.payment.failed";

    /** Published when a refund completes successfully for a previously charged payment. */
    public static final String PAYMENT_REFUND_COMPLETED = "events.payment.refund-completed";

    public static final String DLT_SUFFIX = "-dlt";
}

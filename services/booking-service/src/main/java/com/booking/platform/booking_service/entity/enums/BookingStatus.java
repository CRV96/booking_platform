package com.booking.platform.booking_service.entity.enums;

/**
 * Full production-grade status machine for a booking.
 *
 * <pre>
 * Allowed transitions:
 *
 *   PENDING            → PAYMENT_PROCESSING  (payment charge initiated)
 *   PENDING            → CANCELLED           (hold expired or user cancelled before payment)
 *   PAYMENT_PROCESSING → CONFIRMED           (payment succeeded)
 *   PAYMENT_PROCESSING → CANCELLED           (payment failed)
 *   CONFIRMED          → CANCELLED           (user cancels post-confirmation)
 *   CANCELLED          → REFUND_PENDING      (refund initiated for a paid booking)
 *   REFUND_PENDING     → REFUNDED            (refund completed)
 * </pre>
 *
 * Terminal states: {@link #CONFIRMED} (no refund path), {@link #REFUNDED}.
 * {@link #CANCELLED} is semi-terminal — it transitions to {@link #REFUND_PENDING}
 * only if the original booking was already paid (CONFIRMED → CANCELLED).
 */
public enum BookingStatus {

    /** Initial state — seats are held, awaiting payment initiation. */
    PENDING,

    /** Payment charge is in progress — seats still held. */
    PAYMENT_PROCESSING,

    /** Payment succeeded — booking is active and confirmed. */
    CONFIRMED,

    /**
     * Booking cancelled by user, system (hold expired), or payment failure.
     * Transitions to {@link #REFUND_PENDING} if the booking had already been paid.
     */
    CANCELLED,

    /** Cancellation accepted for a paid booking — refund is being processed. */
    REFUND_PENDING,

    /** Refund has been completed successfully. Terminal state. */
    REFUNDED
}

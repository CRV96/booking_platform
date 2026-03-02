package com.booking.platform.payment_service.entity;

/**
 * Status machine for a payment transaction.
 *
 * <pre>
 * Allowed transitions:
 *
 *   INITIATED        → PROCESSING        (charge request sent to payment gateway)
 *   PROCESSING       → COMPLETED         (payment gateway confirmed success)
 *   PROCESSING       → FAILED            (payment gateway declined or timed out)
 *   COMPLETED        → REFUND_INITIATED  (refund requested — e.g. booking cancelled)
 *   REFUND_INITIATED → REFUNDED          (refund confirmed by payment gateway)
 * </pre>
 *
 * Terminal states: {@link #COMPLETED} (if no refund), {@link #FAILED}, {@link #REFUNDED}.
 */
public enum PaymentStatus {

    /** Initial state — payment record created, charge not yet sent to gateway. */
    INITIATED,

    /** Charge request sent to payment gateway — awaiting confirmation. */
    PROCESSING,

    /** Payment gateway confirmed the charge — funds captured. */
    COMPLETED,

    /** Payment gateway declined the charge or it timed out. */
    FAILED,

    /** Refund has been requested — waiting for gateway confirmation. */
    REFUND_INITIATED,

    /** Refund completed successfully. Terminal state. */
    REFUNDED
}

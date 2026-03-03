package com.booking.platform.payment_service.entity;

/**
 * Status machine for a payment transaction.
 *
 * <pre>
 * Allowed transitions:
 *
 *   INITIATED        → PROCESSING        (charge request sent to payment gateway)
 *   PROCESSING       → COMPLETED         (payment gateway confirmed success)
 *   PROCESSING       → FAILED            (payment gateway declined — business failure)
 *   PROCESSING       → PENDING_RETRY     (gateway temporarily unavailable — circuit open / timeout)
 *   PENDING_RETRY    → PROCESSING        (retry attempt by scheduler)
 *   PENDING_RETRY    → FAILED            (max retries exhausted)
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

    /** Gateway temporarily unavailable (circuit open, timeout, bulkhead full). Will be retried. */
    PENDING_RETRY,

    /** Payment gateway confirmed the charge — funds captured. */
    COMPLETED,

    /** Payment gateway declined the charge (business failure — card declined, invalid amount). */
    FAILED,

    /** Refund has been requested — waiting for gateway confirmation. */
    REFUND_INITIATED,

    /** Refund completed successfully. Terminal state. */
    REFUNDED
}

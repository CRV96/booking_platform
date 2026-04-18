package com.booking.platform.payment_service.entity.enums;

import java.util.Map;
import java.util.Set;

/**
 * Status machine for a payment transaction.
 *
 * <pre>
 * Allowed transitions:
 *
 *   INITIATED        → PROCESSING        (charge request sent to payment gateway)
 *   INITIATED        → PENDING_RETRY     (createPaymentIntent failed — gateway unavailable before PROCESSING)
 *   INITIATED        → FAILED            (createPaymentIntent failed — business error before PROCESSING)
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
 *
 * <p>Valid transitions are enforced at runtime via {@link #canTransitionTo(PaymentStatus)}.
 * {@link com.booking.platform.payment_service.service.impl.PaymentStateTransitionService}
 * calls this before every status change so invalid transitions are caught early
 * rather than silently corrupting payment state.
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
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = Map.of(
            INITIATED,        Set.of(PROCESSING, PENDING_RETRY, FAILED),
            PROCESSING,       Set.of(PROCESSING, COMPLETED, FAILED, PENDING_RETRY),
            PENDING_RETRY,    Set.of(PROCESSING, FAILED),
            COMPLETED,        Set.of(REFUND_INITIATED),
            FAILED,           Set.of(),
            REFUND_INITIATED, Set.of(REFUNDED),
            REFUNDED,         Set.of()
        );
    }

    /**
     * Returns {@code true} if transitioning from this status to {@code next} is allowed
     * by the state machine.
     *
     * @param next the target status
     * @return {@code true} if the transition is valid
     */
    public boolean canTransitionTo(PaymentStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}

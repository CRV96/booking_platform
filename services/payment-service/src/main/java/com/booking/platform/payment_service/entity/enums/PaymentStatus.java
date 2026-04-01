package com.booking.platform.payment_service.entity.enums;

import java.util.Set;

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
 *
 * <p>Valid transitions are enforced at runtime via {@link #canTransitionTo(PaymentStatus)}.
 * {@link com.booking.platform.payment_service.service.impl.PaymentStateTransitionService}
 * calls this before every status change so invalid transitions are caught early
 * rather than silently corrupting payment state.
 */
public enum PaymentStatus {

    /** Initial state — payment record created, charge not yet sent to gateway. */
    INITIATED(Set.of("PROCESSING")),

    /** Charge request sent to payment gateway — awaiting confirmation. */
    PROCESSING(Set.of("COMPLETED", "FAILED", "PENDING_RETRY")),

    /** Gateway temporarily unavailable (circuit open, timeout, bulkhead full). Will be retried. */
    PENDING_RETRY(Set.of("PROCESSING", "FAILED")),

    /** Payment gateway confirmed the charge — funds captured. */
    COMPLETED(Set.of("REFUND_INITIATED")),

    /** Payment gateway declined the charge (business failure — card declined, invalid amount). */
    FAILED(Set.of()),

    /** Refund has been requested — waiting for gateway confirmation. */
    REFUND_INITIATED(Set.of("REFUNDED")),

    /** Refund completed successfully. Terminal state. */
    REFUNDED(Set.of());

    private final Set<String> validNextStatusNames;

    PaymentStatus(Set<String> validNextStatusNames) {
        this.validNextStatusNames = validNextStatusNames;
    }

    /**
     * Returns {@code true} if transitioning from this status to {@code next} is allowed
     * by the state machine.
     *
     * @param next the target status
     * @return {@code true} if the transition is valid
     */
    public boolean canTransitionTo(PaymentStatus next) {
        return validNextStatusNames.contains(next.name());
    }
}

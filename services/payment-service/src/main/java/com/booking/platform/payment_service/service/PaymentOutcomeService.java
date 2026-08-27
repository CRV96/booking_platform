package com.booking.platform.payment_service.service;

/**
 * Applies the outcome of a client-side payment to the payment record.
 *
 * <p>This is the single seam that every payment-outcome source funnels into — the real
 * <b>Stripe webhook</b> and the local <b>mock</b> confirm endpoint both depend on this
 * abstraction, not on a concrete implementation. Whichever calls it, the domain logic
 * (idempotency, optimistic-lock handling, and the state transition that writes the outbox
 * event) runs identically.
 */
public interface PaymentOutcomeService {

    /**
     * Records that the gateway payment succeeded, moving the payment to COMPLETED and
     * publishing {@code PaymentCompleted} (via the outbox).
     *
     * <p>Idempotent and concurrency-safe: a duplicate or racing signal for an
     * already-resolved payment is a no-op.
     *
     * @param externalPaymentId the gateway's PaymentIntent id (e.g. Stripe {@code pi_...})
     */
    void markSucceeded(String externalPaymentId);

    /**
     * Records that the gateway payment failed, moving the payment to FAILED and publishing
     * {@code PaymentFailed} (via the outbox), which triggers the booking's compensation path.
     *
     * <p>Idempotent and concurrency-safe: it will not overwrite an already-resolved payment.
     *
     * @param externalPaymentId the gateway's PaymentIntent id
     * @param reason            human-readable failure reason (e.g. {@code card_declined})
     */
    void markFailed(String externalPaymentId, String reason);
}

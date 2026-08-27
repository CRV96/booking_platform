package com.booking.platform.payment_service.service;

import com.stripe.exception.SignatureVerificationException;

/**
 * Handles inbound Stripe webhook calls: verifies the payload signature and dispatches the
 * event to the {@link PaymentOutcomeService}.
 *
 * <p>This is the authoritative signal that a client-side payment resolved — the browser's
 * result is only used for UX. Only exists in {@code stripe} gateway mode.
 */
public interface StripeWebhookService {

    /**
     * Verifies the webhook signature and applies the event's outcome.
     *
     * @param payload         the raw request body exactly as received (required for signature verification)
     * @param signatureHeader the value of the {@code Stripe-Signature} header
     * @throws SignatureVerificationException if the payload signature is invalid (caller should reply 400)
     */
    void process(String payload, String signatureHeader) throws SignatureVerificationException;
}

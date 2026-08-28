package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.service.PaymentOutcomeService;
import com.booking.platform.payment_service.service.StripeWebhookService;
import com.booking.platform.common.logging.ApplicationLogger;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stripe implementation of {@link StripeWebhookService}.
 *
 * <p>Active only when {@code payment.gateway.type=stripe} — the mock gateway drives outcomes
 * through its own confirm endpoint instead of a real webhook.
 *
 * <p>The heavy lifting (idempotency, optimistic-lock handling, the state transition + outbox
 * write) lives in {@link PaymentOutcomeService}. This class only verifies Stripe's signature
 * and translates the event into a call on that seam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = BkgConstants.BkgStripeConstants.PAYMENT_GATEWAY_TYPE,
        havingValue = BkgConstants.BkgStripeConstants.STRIPE)
public class StripeWebhookServiceImpl implements StripeWebhookService {

    private static final String UNKNOWN_FAILURE_REASON = "payment_failed";

    private final PaymentOutcomeService paymentOutcomeService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Override
    public void process(String payload, String signatureHeader) throws SignatureVerificationException {
        // Verifies the HMAC signature against our webhook secret — proves the call genuinely
        // came from Stripe and the body wasn't tampered with. Throws if it doesn't match.
        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);

        ApplicationLogger.logMessage(log, Level.INFO, "[STRIPE_WEBHOOK] event id='{}', type='{}'",
                event.getId(), event.getType());

        switch (event.getType()) {
            case BkgConstants.BkgStripeConstants.EVENT_PAYMENT_INTENT_SUCCEEDED -> {
                PaymentIntent intent = extractPaymentIntent(event);
                paymentOutcomeService.markSucceeded(intent.getId());
            }
            case BkgConstants.BkgStripeConstants.EVENT_PAYMENT_INTENT_PAYMENT_FAILED -> {
                PaymentIntent intent = extractPaymentIntent(event);
                paymentOutcomeService.markFailed(intent.getId(), failureReason(intent));
            }
            // Stripe sends many event types; we only act on the two we subscribed to. Others are
            // acknowledged (200) and ignored so Stripe doesn't retry them.
            default -> ApplicationLogger.logMessage(log, Level.DEBUG,
                    "[STRIPE_WEBHOOK] ignoring event type='{}'", event.getType());
        }
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize PaymentIntent from event id='" + event.getId() + "'"));
    }

    private String failureReason(PaymentIntent intent) {
        return intent.getLastPaymentError() != null
                ? intent.getLastPaymentError().getMessage()
                : UNKNOWN_FAILURE_REASON;
    }
}

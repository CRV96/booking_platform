package com.booking.platform.payment_service.controller;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.service.StripeWebhookService;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe webhook callbacks. This endpoint is public (no JWT) — authenticity is
 * proven by the Stripe signature, verified in {@link StripeWebhookService}.
 *
 * <p>The HTTP status is how we tell Stripe whether to retry delivery:
 * <ul>
 *   <li><b>200</b> — recorded (or safely ignored); stop.</li>
 *   <li><b>400</b> — bad signature; a retry won't fix it, so don't retry.</li>
 *   <li><b>500</b> — transient processing error; Stripe should redeliver later.</li>
 * </ul>
 *
 * <p>Only exists in {@code stripe} gateway mode.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = BkgConstants.BkgStripeConstants.PAYMENT_GATEWAY_TYPE,
        havingValue = BkgConstants.BkgStripeConstants.STRIPE)
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping(BkgConstants.BkgStripeConstants.WEBHOOK_PATH)
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader(BkgConstants.BkgStripeConstants.WEBHOOK_SIGNATURE_HEADER) String signature) {
        try {
            stripeWebhookService.process(payload, signature);
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            ApplicationLogger.logMessage(log, Level.WARN,
                    "[STRIPE_WEBHOOK] invalid signature — rejecting: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.PAYMENT_PROCESSING_FAILED,
                    "[STRIPE_WEBHOOK] processing error — asking Stripe to retry: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

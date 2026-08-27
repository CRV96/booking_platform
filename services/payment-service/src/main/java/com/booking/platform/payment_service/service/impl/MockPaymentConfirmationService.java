package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentOutcomeService;
import com.booking.platform.payment_service.util.MockTestCards;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Simulates the payment outcome in mock mode — the mock's equivalent of the Stripe webhook.
 *
 * <p><b>Mock mode only.</b> Gated to {@code payment.gateway.type=mock} (the default). It must
 * never exist in stripe mode, because it moves a payment to COMPLETED without a real charge —
 * exposing it with real Stripe would let anyone grant themselves a paid booking.
 *
 * <p>Maps the (fake) test card number to an outcome via {@link MockTestCards} and applies it
 * through the shared {@link PaymentOutcomeService} — so a mock decline exercises the real
 * failure/compensation path, not a shortcut.
 */
@Slf4j
@Service
@ConditionalOnProperty(
        name = BkgConstants.BkgStripeConstants.PAYMENT_GATEWAY_TYPE,
        havingValue = "mock",
        matchIfMissing = true)
@RequiredArgsConstructor
public class MockPaymentConfirmationService {

    private static final String REASON_CARD_DECLINED = "card_declined";
    private static final String REASON_INSUFFICIENT_FUNDS = "insufficient_funds";

    private final PaymentRepository paymentRepository;
    private final PaymentOutcomeService paymentOutcomeService;

    // Configured test cards — empty (e.g. in production) disables that outcome. See docs/payment-test-cards.md.
    @Value("${payment.mock.card.success:}")
    private String successCard;

    @Value("${payment.mock.card.requires-auth:}")
    private String requiresAuthCard;

    @Value("${payment.mock.card.insufficient-funds:}")
    private String insufficientFundsCard;

    /**
     * Applies the simulated outcome for a booking's payment and returns the updated record.
     *
     * @param bookingId  the booking whose payment to resolve
     * @param cardNumber the test card selecting the outcome
     * @return the payment after the outcome is applied (fresh status)
     */
    public PaymentEntity confirm(String bookingId, String cardNumber) {
        PaymentEntity payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No payment found for booking " + bookingId));
        String externalId = payment.getExternalPaymentId();

        MockTestCards.Outcome outcome =
                MockTestCards.classify(cardNumber, successCard, requiresAuthCard, insufficientFundsCard);
        ApplicationLogger.logMessage(log, Level.INFO,
                "[MOCK CONFIRM] bookingId='{}', externalId='{}', outcome={}", bookingId, externalId, outcome);

        switch (outcome) {
            case SUCCESS, REQUIRES_AUTH -> paymentOutcomeService.markSucceeded(externalId);
            case DECLINED -> paymentOutcomeService.markFailed(externalId, REASON_CARD_DECLINED);
            case INSUFFICIENT_FUNDS -> paymentOutcomeService.markFailed(externalId, REASON_INSUFFICIENT_FUNDS);
        }

        // Re-read so the caller gets the committed post-outcome status (COMPLETED / FAILED).
        return paymentRepository.findByBookingId(bookingId).orElse(payment);
    }
}

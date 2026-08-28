package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentOutcomeService;
import com.booking.platform.payment_service.util.PaymentStatusUtil;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies the outcome of a client-side payment to our payment record.
 *
 * <p>This is the single seam that both payment-outcome sources funnel into:
 * <ul>
 *   <li>the real <b>Stripe webhook</b> ({@code payment_intent.succeeded} / {@code .payment_failed}), and</li>
 *   <li>the local <b>mock</b> confirm endpoint.</li>
 * </ul>
 * Whichever calls it, the domain logic — idempotency, optimistic-lock handling, and the
 * state transition that writes the outbox event — runs identically here.
 *
 * <p><b>Idempotency &amp; concurrency.</b> The same success signal can arrive more than once
 * (Stripe retries webhooks) or two signals can race (webhook + a status re-check):
 * <ol>
 *   <li>A pre-check short-circuits when the payment is already in a terminal state (the common
 *       duplicate case) — an idempotent no-op.</li>
 *   <li>A simultaneous race is caught via {@link ObjectOptimisticLockingFailureException} (both
 *       writers read the same {@code @Version}) or {@link IllegalStateException} (the second
 *       reads the already-terminal state and fails the transition guard). In either case we
 *       re-read: if a concurrent handler already resolved it, we no-op (success); otherwise the
 *       state is unexpected and we rethrow so the caller can signal a retry.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutcomeServiceImpl implements PaymentOutcomeService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionService transitions;

    // Log labels identifying which outcome path produced a message.
    private static final String OUTCOME_SUCCEEDED = "SUCCEEDED";
    private static final String OUTCOME_FAILED = "FAILED";

    @Override
    public void markSucceeded(String externalPaymentId) {
        PaymentEntity payment = findOrIgnore(externalPaymentId, OUTCOME_SUCCEEDED);
        if (payment == null || alreadyResolved(payment, OUTCOME_SUCCEEDED)) {
            return;
        }
        GatewayPaymentResponse response = new GatewayPaymentResponse(
                externalPaymentId,
                BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED,
                BkgConstants.BkgStripeConstants.CARD_PAYMENT_METHOD);
        try {
            transitions.markCompleted(payment.getId(), response);
            ApplicationLogger.logMessage(log, Level.INFO,
                    "Payment COMPLETED via outcome signal: id='{}', externalId='{}'",
                    payment.getId(), externalPaymentId);
        } catch (ObjectOptimisticLockingFailureException | IllegalStateException e) {
            reconcileConcurrentOutcome(payment.getId(), OUTCOME_SUCCEEDED, e);
        }
    }

    @Override
    public void markFailed(String externalPaymentId, String reason) {
        PaymentEntity payment = findOrIgnore(externalPaymentId, OUTCOME_FAILED);
        if (payment == null || alreadyResolved(payment, OUTCOME_FAILED)) {
            return;
        }
        try {
            transitions.markFailed(payment.getId(), reason);
            ApplicationLogger.logMessage(log, Level.INFO,
                    "Payment FAILED via outcome signal: id='{}', externalId='{}', reason='{}'",
                    payment.getId(), externalPaymentId, reason);
        } catch (ObjectOptimisticLockingFailureException | IllegalStateException e) {
            reconcileConcurrentOutcome(payment.getId(), OUTCOME_FAILED, e);
        }
    }

    private PaymentEntity findOrIgnore(String externalPaymentId, String outcome) {
        Optional<PaymentEntity> found = paymentRepository.findByExternalPaymentId(externalPaymentId);
        if (found.isEmpty()) {
            // No local record for this PaymentIntent — not ours, or a stray event. Acknowledge and ignore.
            ApplicationLogger.logMessage(log, Level.WARN,
                    "[{}] No payment found for externalId='{}' — ignoring outcome signal", outcome, externalPaymentId);
            return null;
        }
        return found.get();
    }

    private boolean alreadyResolved(PaymentEntity payment, String outcome) {
        if (PaymentStatusUtil.isResolved(payment.getStatus())) {
            ApplicationLogger.logMessage(log, Level.INFO,
                    "[{}] Payment id='{}' already resolved (status={}) — idempotent no-op",
                    outcome, payment.getId(), payment.getStatus());
            return true;
        }
        return false;
    }

    /**
     * A concurrent handler moved the row between our pre-check and our write. Re-read the truth:
     * if it's already resolved, the other handler did our job (no-op = success); otherwise the
     * state is unexpected, so rethrow and let the caller (e.g. the webhook) signal a retry.
     */
    private void reconcileConcurrentOutcome(UUID paymentId, String outcome, RuntimeException conflict) {
        PaymentEntity fresh = paymentRepository.findById(paymentId).orElse(null);
        if (fresh != null && PaymentStatusUtil.isResolved(fresh.getStatus())) {
            ApplicationLogger.logMessage(log, Level.INFO,
                    "[{}] Payment id='{}' already resolved concurrently (status={}) — no-op",
                    outcome, paymentId, fresh.getStatus());
            return;
        }
        ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.PAYMENT_PROCESSING_FAILED,
                "[{}] Conflict on payment id='{}' but state is {} — rethrowing for retry",
                outcome, paymentId, fresh == null ? "MISSING" : fresh.getStatus());
        throw conflict;
    }
}

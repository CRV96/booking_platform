package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Stripe SDK implementation of {@link PaymentGateway}, decorated with Resilience4j.
 *
 * <p>Activated when {@code payment.gateway.type=stripe} in configuration.
 *
 * <p><b>Resilience4j annotations (P4-03)</b> — each method is protected by four layers:
 * <ol>
 *   <li>{@code @Retry}          — retries on transient failures (3 attempts, exponential backoff 1s/2s/4s)</li>
 *   <li>{@code @CircuitBreaker}  — opens after 5 failures in 10 calls, stays open 30s, then probes</li>
 *   <li>{@code @TimeLimiter}     — cancels the call if Stripe doesn't respond within 10s</li>
 *   <li>{@code @Bulkhead}        — limits to 20 concurrent Stripe calls (semaphore-based)</li>
 * </ol>
 *
 * <p>Execution order (outermost → innermost):
 * {@code Retry → CircuitBreaker → TimeLimiter → Bulkhead → method}
 *
 * <p>When the circuit is OPEN or all retries are exhausted, the fallback throws
 * {@link PaymentGatewayUnavailableException} to signal the service layer to mark
 * the payment as {@code PENDING_RETRY}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = BkgConstants.BkgStripeConstants.PAYMENT_GATEWAY_TYPE, havingValue = BkgConstants.BkgStripeConstants.STRIPE)
public class StripePaymentGateway implements PaymentGateway {

    @Value("${stripe.api-key}")
    private String apiKey;

    @PostConstruct
    void init() {
        Stripe.apiKey = apiKey;
        log.info("Stripe gateway initialized");
    }

    // ── Gateway methods (decorated with Resilience4j) ────────────────────────

    @Override
    @Retry(name = BkgConstants.BkgStripeConstants.STRIPE)
    @CircuitBreaker(name = BkgConstants.BkgStripeConstants.STRIPE, fallbackMethod = "createPaymentIntentFallback")
    @TimeLimiter(name = BkgConstants.BkgStripeConstants.STRIPE)
    @Bulkhead(name = BkgConstants.BkgStripeConstants.STRIPE, type = Bulkhead.Type.SEMAPHORE)
    public CompletableFuture<GatewayPaymentResponse> createPaymentIntent(
            BigDecimal amount, String currency, String idempotencyKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

                PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(amountInCents)
                        .setCurrency(currency.toLowerCase())
                        .putMetadata("idempotency_key", idempotencyKey)
                        .addPaymentMethodType("card")
                        .build();

                PaymentIntent intent = PaymentIntent.create(params);

                log.info("Stripe PaymentIntent created: id='{}', status='{}', amount={} {}",
                        intent.getId(), intent.getStatus(), amount, currency);

                return new GatewayPaymentResponse(intent.getId(), intent.getStatus(), "card");

            } catch (StripeException e) {
                log.error("Stripe createPaymentIntent failed: code='{}', message='{}'",
                        e.getCode(), e.getMessage());
                throw new PaymentGatewayException("Failed to create payment intent: " + e.getMessage(), e);
            }
        });
    }

    @Override
    @Retry(name = BkgConstants.BkgStripeConstants.STRIPE)
    @CircuitBreaker(name = BkgConstants.BkgStripeConstants.STRIPE, fallbackMethod = "confirmPaymentFallback")
    @TimeLimiter(name = BkgConstants.BkgStripeConstants.STRIPE)
    @Bulkhead(name = BkgConstants.BkgStripeConstants.STRIPE, type = Bulkhead.Type.SEMAPHORE)
    public CompletableFuture<GatewayPaymentResponse> confirmPayment(String externalPaymentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PaymentIntent intent = PaymentIntent.retrieve(externalPaymentId);

                PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                        .setPaymentMethod(BkgConstants.BkgStripeConstants.PAYMENT_METHOD_CARD_VISA) // Stripe test payment method
                        .build();

                intent = intent.confirm(params);

                log.info("Stripe PaymentIntent confirmed: id='{}', status='{}'",
                        intent.getId(), intent.getStatus());

                String paymentMethod = intent.getPaymentMethod() != null
                        ? intent.getPaymentMethod()
                        : "card";

                return new GatewayPaymentResponse(intent.getId(), intent.getStatus(), paymentMethod);

            } catch (StripeException e) {
                log.error("Stripe confirmPayment failed: code='{}', message='{}'",
                        e.getCode(), e.getMessage());
                throw new PaymentGatewayException("Failed to confirm payment: " + e.getMessage(), e);
            }
        });
    }

    @Override
    @Retry(name = BkgConstants.BkgStripeConstants.STRIPE)
    @CircuitBreaker(name = BkgConstants.BkgStripeConstants.STRIPE, fallbackMethod = "createRefundFallback")
    @TimeLimiter(name = BkgConstants.BkgStripeConstants.STRIPE)
    @Bulkhead(name = BkgConstants.BkgStripeConstants.STRIPE, type = Bulkhead.Type.SEMAPHORE)
    public CompletableFuture<GatewayRefundResponse> createRefund(
            String externalPaymentId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

                RefundCreateParams params = RefundCreateParams.builder()
                        .setPaymentIntent(externalPaymentId)
                        .setAmount(amountInCents)
                        .build();

                Refund refund = Refund.create(params);

                log.info("Stripe Refund created: id='{}', status='{}', amount={}",
                        refund.getId(), refund.getStatus(), amount);

                return new GatewayRefundResponse(refund.getId(), refund.getStatus());

            } catch (StripeException e) {
                log.error("Stripe createRefund failed: code='{}', message='{}'",
                        e.getCode(), e.getMessage());
                throw new PaymentGatewayException("Failed to create refund: " + e.getMessage(), e);
            }
        });
    }

    // ── Fallback methods ────────────────────────────────────────────────────────
    // Called when the circuit breaker is OPEN or after all retries are exhausted.
    // They throw PaymentGatewayUnavailableException to signal the service layer
    // to mark the payment as PENDING_RETRY (not permanently FAILED).

    private CompletableFuture<GatewayPaymentResponse> createPaymentIntentFallback(
            BigDecimal amount, String currency, String idempotencyKey, Throwable t) {
        log.warn("FALLBACK createPaymentIntent: amount={} {}, key='{}', cause='{}'",
                amount, currency, idempotencyKey, t.getMessage());
        return CompletableFuture.failedFuture(
                new PaymentGatewayUnavailableException(
                        "Stripe unavailable for createPaymentIntent: " + t.getMessage(), t));
    }

    private CompletableFuture<GatewayPaymentResponse> confirmPaymentFallback(
            String externalPaymentId, Throwable t) {
        log.warn("FALLBACK confirmPayment: externalId='{}', cause='{}'",
                externalPaymentId, t.getMessage());
        return CompletableFuture.failedFuture(
                new PaymentGatewayUnavailableException(
                        "Stripe unavailable for confirmPayment: " + t.getMessage(), t));
    }

    private CompletableFuture<GatewayRefundResponse> createRefundFallback(
            String externalPaymentId, BigDecimal amount, Throwable t) {
        log.warn("FALLBACK createRefund: externalId='{}', amount={}, cause='{}'",
                externalPaymentId, amount, t.getMessage());
        return CompletableFuture.failedFuture(
                new PaymentGatewayUnavailableException(
                        "Stripe unavailable for createRefund: " + t.getMessage(), t));
    }
}

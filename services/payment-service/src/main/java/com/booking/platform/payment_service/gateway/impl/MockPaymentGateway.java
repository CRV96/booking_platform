package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mock implementation of {@link PaymentGateway} for local development and testing.
 *
 * <p>Activated when {@code payment.gateway.type=mock} or when the property is missing
 * ({@code matchIfMissing = true}). All operations auto-succeed after a 2-second delay
 * to simulate network latency.
 *
 * <p>No Resilience4j annotations here — the mock is deterministic and doesn't need
 * circuit breaker protection.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = BkgConstants.BkgStripeConstants.PAYMENT_GATEWAY_TYPE, havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Value("${payment.gateway.mock.delay-ms:2000}")
    private static long SIMULATED_DELAY_MS;

    @Override
    public CompletableFuture<GatewayPaymentResponse> createPaymentIntent(
            BigDecimal amount, String currency, String idempotencyKey) {
        return CompletableFuture.supplyAsync(() -> {

            log.info("[MOCK] Creating payment intent: amount={} {}, idempotencyKey='{}'",
                    amount, currency, idempotencyKey);
            simulateDelay();
            String mockId = "mock_pi_" + UUID.randomUUID();

            log.info("[MOCK] Payment intent created: id='{}'", mockId);

            return new GatewayPaymentResponse(mockId, "requires_confirmation",
                    BkgConstants.BkgStripeConstants.CARD_PAYMENT_METHOD);
        });
    }

    @Override
    public CompletableFuture<GatewayPaymentResponse> confirmPayment(String externalPaymentId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[MOCK] Confirming payment: id='{}'", externalPaymentId);
            simulateDelay();
            log.info("[MOCK] Payment confirmed: id='{}'", externalPaymentId);

            return new GatewayPaymentResponse(externalPaymentId, BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED,
                    BkgConstants.BkgStripeConstants.CARD_PAYMENT_METHOD);
        });
    }

    @Override
    public CompletableFuture<GatewayRefundResponse> createRefund(
            String externalPaymentId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[MOCK] Creating refund: paymentId='{}', amount={}", externalPaymentId, amount);
            simulateDelay();
            String mockRefundId = "mock_re_" + UUID.randomUUID();
            log.info("[MOCK] Refund created: id='{}'", mockRefundId);
            return new GatewayRefundResponse(mockRefundId, BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED);
        });
    }

    private void simulateDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

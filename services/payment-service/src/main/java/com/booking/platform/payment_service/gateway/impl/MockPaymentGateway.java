package com.booking.platform.payment_service.gateway.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock implementation of {@link PaymentGateway} for local development and testing.
 *
 * <p>Activated when {@code payment.gateway.type=mock} or when the property is missing
 * ({@code matchIfMissing = true}). All operations auto-succeed after a 2-second delay
 * to simulate network latency.
 *
 * <p>Replaces the old {@code payment.stub.failure-rate} approach from P3-07.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.type", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    //TODO: move the delay value to configuration if we want to experiment with different latencies
    private static final long SIMULATED_DELAY_MS = 2000;

    @Override
    public GatewayPaymentResponse createPaymentIntent(BigDecimal amount, String currency, String idempotencyKey) {
        log.info("[MOCK] Creating payment intent: amount={} {}, idempotencyKey='{}'",
                amount, currency, idempotencyKey);

        simulateDelay();

        String mockId = "mock_pi_" + UUID.randomUUID();
        log.info("[MOCK] Payment intent created: id='{}'", mockId);

        return new GatewayPaymentResponse(mockId, "requires_confirmation", "card");
    }

    @Override
    public GatewayPaymentResponse confirmPayment(String externalPaymentId) {
        log.info("[MOCK] Confirming payment: id='{}'", externalPaymentId);

        simulateDelay();

        log.info("[MOCK] Payment confirmed: id='{}'", externalPaymentId);

        return new GatewayPaymentResponse(externalPaymentId, "succeeded", "card");
    }

    @Override
    public GatewayRefundResponse createRefund(String externalPaymentId, BigDecimal amount) {
        log.info("[MOCK] Creating refund: paymentId='{}', amount={}", externalPaymentId, amount);

        simulateDelay();

        String mockRefundId = "mock_re_" + UUID.randomUUID();
        log.info("[MOCK] Refund created: id='{}'", mockRefundId);

        return new GatewayRefundResponse(mockRefundId, "succeeded");
    }

    private void simulateDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

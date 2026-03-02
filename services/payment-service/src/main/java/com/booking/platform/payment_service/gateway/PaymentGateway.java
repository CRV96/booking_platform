package com.booking.platform.payment_service.gateway;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;

import java.math.BigDecimal;

/**
 * Abstraction over a payment gateway (Stripe, mock, etc.).
 *
 * <p>Implementations are selected at startup via {@code @ConditionalOnProperty("payment.gateway.type")}:
 * <ul>
 *   <li>{@code stripe} — real Stripe SDK calls (test or live mode depending on API key)</li>
 *   <li>{@code mock}   — auto-succeeds after a short delay (default when property is missing)</li>
 * </ul>
 *
 * <p>This interface is designed for P4-03 (Resilience4j) to wrap with circuit breaker / retry.
 */
public interface PaymentGateway {

    /**
     * Creates a payment intent on the gateway.
     *
     * @param amount         payment amount in the currency's standard unit (e.g. dollars, not cents)
     * @param currency       ISO 4217 currency code (e.g. "USD")
     * @param idempotencyKey client-generated key to prevent duplicate charges
     * @return gateway response with external ID and status
     */
    GatewayPaymentResponse createPaymentIntent(BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Confirms a previously created payment intent.
     *
     * @param externalPaymentId the gateway's payment ID (e.g. Stripe PaymentIntent ID)
     * @return gateway response with updated status
     */
    GatewayPaymentResponse confirmPayment(String externalPaymentId);

    /**
     * Creates a refund for a completed payment.
     *
     * @param externalPaymentId the gateway's payment ID to refund
     * @param amount            refund amount (partial or full)
     * @return gateway response with refund ID and status
     */
    GatewayRefundResponse createRefund(String externalPaymentId, BigDecimal amount);
}

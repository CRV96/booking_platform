package com.booking.platform.payment_service.gateway;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over a payment gateway (Stripe, mock, etc.).
 *
 * <p>Implementations are selected at startup via {@code @ConditionalOnProperty("payment.gateway.type")}:
 * <ul>
 *   <li>{@code stripe} — real Stripe SDK calls, decorated with Resilience4j annotations</li>
 *   <li>{@code mock}   — auto-succeeds after a short delay (default when property is missing)</li>
 * </ul>
 *
 * <p>All methods return {@link CompletableFuture} to support Resilience4j's
 * {@code @TimeLimiter}, which requires async return types. Implementations wrap
 * synchronous gateway SDK calls in {@code CompletableFuture.supplyAsync()}.
 */
public interface PaymentGateway {

    /**
     * Creates a payment intent on the gateway.
     *
     * @param amount         payment amount in the currency's standard unit (e.g. dollars, not cents)
     * @param currency       ISO 4217 currency code (e.g. "USD")
     * @param idempotencyKey client-generated key to prevent duplicate charges
     * @return future with gateway response containing external ID and status
     */
    CompletableFuture<GatewayPaymentResponse> createPaymentIntent(BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Confirms a previously created payment intent.
     *
     * @param externalPaymentId the gateway's payment ID (e.g. Stripe PaymentIntent ID)
     * @return future with gateway response containing updated status
     */
    CompletableFuture<GatewayPaymentResponse> confirmPayment(String externalPaymentId);

    /**
     * Creates a refund for a completed payment.
     *
     * @param externalPaymentId the gateway's payment ID to refund
     * @param amount            refund amount (partial or full)
     * @return future with gateway response containing refund ID and status
     */
    CompletableFuture<GatewayRefundResponse> createRefund(String externalPaymentId, BigDecimal amount);
}

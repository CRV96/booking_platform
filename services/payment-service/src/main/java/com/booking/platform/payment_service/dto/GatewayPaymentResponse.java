package com.booking.platform.payment_service.dto;

/**
 * Response from the payment gateway after creating or confirming a payment intent.
 *
 * @param externalPaymentId gateway's own payment identifier (e.g. Stripe PaymentIntent ID: "pi_1234...")
 * @param status            gateway-reported status (e.g. "succeeded", "requires_confirmation", "requires_action")
 * @param paymentMethod     payment method type used (e.g. "card", "bank_transfer")
 * @param clientSecret      Stripe PaymentIntent client secret ("pi_123..._secret_456"). Handed to the
 *                          browser so it can confirm the payment client-side (Stripe Elements). Only
 *                          populated by {@code createPaymentIntent}; {@code null} for confirm/refund
 *                          responses, which never expose a secret to the client.
 */
public record GatewayPaymentResponse(
        String externalPaymentId,
        String status,
        String paymentMethod,
        String clientSecret
) {
    /**
     * Backward-compatible constructor for responses that carry no client secret
     * (confirm, refund). The client secret is only relevant when creating an intent
     * for client-side confirmation.
     */
    public GatewayPaymentResponse(String externalPaymentId, String status, String paymentMethod) {
        this(externalPaymentId, status, paymentMethod, null);
    }
}

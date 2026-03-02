package com.booking.platform.payment_service.dto;

/**
 * Response from the payment gateway after creating or confirming a payment intent.
 *
 * @param externalPaymentId gateway's own payment identifier (e.g. Stripe PaymentIntent ID: "pi_1234...")
 * @param status            gateway-reported status (e.g. "succeeded", "requires_confirmation", "requires_action")
 * @param paymentMethod     payment method type used (e.g. "card", "bank_transfer")
 */
public record GatewayPaymentResponse(
        String externalPaymentId,
        String status,
        String paymentMethod
) {}

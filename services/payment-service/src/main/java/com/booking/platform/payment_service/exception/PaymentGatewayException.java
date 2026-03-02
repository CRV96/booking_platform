package com.booking.platform.payment_service.exception;

/**
 * Thrown when a payment gateway operation fails.
 *
 * <p>Wraps gateway-specific exceptions (e.g. {@link com.stripe.exception.StripeException})
 * so the service layer doesn't depend on a specific gateway SDK.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

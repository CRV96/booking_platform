package com.booking.platform.payment_service.exception;

/**
 * Thrown when the payment gateway is temporarily unavailable.
 *
 * <p>This is distinct from {@link PaymentGatewayException}, which indicates a
 * <b>business-level</b> failure (card declined, invalid amount, etc.).
 * This exception indicates an <b>infrastructure</b> failure:
 * <ul>
 *   <li>Network timeout ({@code @TimeLimiter})</li>
 *   <li>Circuit breaker open ({@code @CircuitBreaker} fallback)</li>
 *   <li>Bulkhead full ({@code @Bulkhead})</li>
 *   <li>All retries exhausted ({@code @Retry})</li>
 * </ul>
 *
 * <p>When this exception reaches the service layer, the payment is marked
 * {@code PENDING_RETRY} (not {@code FAILED}), leaving the door open for
 * a future retry attempt.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }
}

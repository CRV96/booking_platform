package com.booking.platform.graphql_gateway.dto.payment;

import com.booking.platform.common.grpc.payment.PaymentIntentResponse;

/**
 * GraphQL DTO for the result of starting a payment.
 * Maps from the gRPC {@link PaymentIntentResponse} message.
 *
 * <p>{@code clientSecret} is what the browser uses to confirm the card client-side; it is
 * {@code null} when no further card entry is needed (e.g. the payment already completed),
 * in which case the frontend routes to the confirmation page instead of showing the card form.
 */
public record PaymentIntentResult(
        String paymentId,
        String bookingId,
        String externalPaymentId,
        String clientSecret,
        String status
) {
    public static PaymentIntentResult fromGrpc(PaymentIntentResponse response) {
        return new PaymentIntentResult(
                response.getPaymentId(),
                response.getBookingId(),
                emptyToNull(response.getExternalPaymentId()),
                emptyToNull(response.getClientSecret()),
                response.getStatus());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

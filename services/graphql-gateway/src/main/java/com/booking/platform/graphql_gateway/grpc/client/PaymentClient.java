package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.payment.PaymentIntentResponse;

/**
 * gRPC client for calling payment-service.
 */
public interface PaymentClient {

    /**
     * Get-or-create the PaymentIntent for a booking.
     *
     * @param bookingId the booking being paid for
     * @param amount    decimal as string, taken from the authoritative booking (not the client)
     * @param currency  ISO 4217 code
     */
    PaymentIntentResponse createPaymentIntent(String bookingId, String amount, String currency);

    /**
     * Mock mode only — simulate the payment outcome for a booking using a test card number.
     * Rejected by payment-service when it runs against real Stripe.
     */
    PaymentIntentResponse confirmMockPayment(String bookingId, String cardNumber);
}

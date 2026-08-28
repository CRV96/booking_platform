package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.payment.PaymentIntentResponse;

import java.util.List;

/**
 * gRPC client for calling payment-service.
 */
public interface PaymentClient {

    /**
     * Get-or-create one PaymentIntent for an order covering several bookings.
     *
     * @param orderId    client-generated order id (idempotency key)
     * @param bookingIds the bookings this order pays for
     * @param amount     order total as decimal string, summed from the authoritative bookings
     * @param currency   ISO 4217 code
     */
    PaymentIntentResponse createOrderPaymentIntent(String orderId, List<String> bookingIds, String amount, String currency);

    /**
     * Mock mode only — simulate the payment outcome for a booking using a test card number.
     * Rejected by payment-service when it runs against real Stripe.
     */
    PaymentIntentResponse confirmMockPayment(String bookingId, String cardNumber);
}

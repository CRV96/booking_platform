package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.booking.BookingInfo;
import com.booking.platform.graphql_gateway.dto.payment.PaymentIntentResult;
import com.booking.platform.graphql_gateway.grpc.client.BookingClient;
import com.booking.platform.graphql_gateway.grpc.client.PaymentClient;
import com.booking.platform.graphql_gateway.service.AuthService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphQL resolver for payment operations.
 *
 * <p>Requires authentication. The amount/currency are read from the authoritative booking
 * record (fetched from booking-service) rather than accepted from the client, so a user can't
 * dictate what they pay. booking-service enforces that the booking belongs to the caller.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PaymentResolver {

    private final PaymentClient paymentClient;
    private final BookingClient bookingClient;
    private final AuthService authService;

    /**
     * Start (or resume) payment for an order covering several bookings. The amount is summed from
     * the authoritative bookings (never the client), which also enforces ownership per booking.
     */
    @MutationMapping
    public PaymentIntentResult createOrderPaymentIntent(@Argument("orderId") String orderId,
                                                        @Argument("bookingIds") List<String> bookingIds) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: createOrderPaymentIntent(order='{}', {} bookings) for user '{}'",
                orderId, bookingIds.size(), userId);

        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        for (String bookingId : bookingIds) {
            BookingInfo booking = bookingClient.getBooking(bookingId).getBooking();
            if (currency == null) {
                currency = booking.getCurrency();
            } else if (!currency.equals(booking.getCurrency())) {
                throw new IllegalArgumentException("All items in an order must use the same currency");
            }
            total = total.add(new BigDecimal(booking.getTotalPrice()));
        }

        return PaymentIntentResult.fromGrpc(
                paymentClient.createOrderPaymentIntent(orderId, bookingIds, total.toPlainString(), currency));
    }

    /**
     * Mock mode only — simulate the payment outcome using a test card number. payment-service
     * rejects this when running against real Stripe (the outcome must come from the webhook there).
     */
    @MutationMapping
    public PaymentIntentResult confirmMockPayment(@Argument("bookingId") String bookingId,
                                                  @Argument("cardNumber") String cardNumber) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: confirmMockPayment(booking='{}') for user '{}'", bookingId, userId);

        return PaymentIntentResult.fromGrpc(paymentClient.confirmMockPayment(bookingId, cardNumber));
    }
}

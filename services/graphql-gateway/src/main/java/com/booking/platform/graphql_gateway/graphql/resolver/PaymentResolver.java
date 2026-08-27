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

    @MutationMapping
    public PaymentIntentResult createPaymentIntent(@Argument("bookingId") String bookingId) {
        String userId = authService.getAuthenticatedUserId();
        ApplicationLogger.logMessage(log, Level.INFO,
                "GraphQL mutation: createPaymentIntent(booking='{}') for user '{}'", bookingId, userId);

        // Authoritative amount/currency come from the booking, never from the client.
        BookingInfo booking = bookingClient.getBooking(bookingId).getBooking();

        return PaymentIntentResult.fromGrpc(
                paymentClient.createPaymentIntent(bookingId, booking.getTotalPrice(), booking.getCurrency()));
    }
}

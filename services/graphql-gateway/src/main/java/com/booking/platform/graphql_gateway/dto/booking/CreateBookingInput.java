package com.booking.platform.graphql_gateway.dto.booking;

/**
 * GraphQL input for creating a new booking.
 *
 * <p>Note: {@code userId} is not included — booking-service extracts it
 * from the JWT forwarded by the gateway's {@code JwtForwardingClientInterceptor}.</p>
 */
public record CreateBookingInput(
        String eventId,
        String seatCategory,
        int quantity,
        String idempotencyKey
) {
}

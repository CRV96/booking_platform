package com.booking.platform.graphql_gateway.dto.cart;

/**
 * GraphQL input for adding a line to the cart. {@code userId} is not part of the input —
 * it is taken from the JWT downstream in booking-service.
 */
public record AddToCartInput(
        String eventId,
        String eventTitle,
        String seatCategory,
        int quantity,
        String unitPrice,
        String currency
) {}

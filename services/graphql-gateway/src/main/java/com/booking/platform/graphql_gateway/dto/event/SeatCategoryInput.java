package com.booking.platform.graphql_gateway.dto.event;

/**
 * GraphQL input for creating a seat category.
 */
public record SeatCategoryInput(
        String name,
        double price,
        String currency,
        int totalSeats
) {
}

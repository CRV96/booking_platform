package com.booking.platform.graphql_gateway.dto.event;

import java.util.List;

/**
 * GraphQL input for creating a new event.
 */
public record CreateEventInput(
        String title,
        String description,
        String category,
        String dateTime,
        VenueInput venue,
        List<SeatCategoryInput> seatCategories
) {
}

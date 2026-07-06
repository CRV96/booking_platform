package com.booking.platform.graphql_gateway.dto.event;

import java.util.List;

/**
 * GraphQL input for updating an existing event.
 * All fields are optional — only non-null fields will be updated.
 */
public record UpdateEventInput(
        String title,
        String description,
        String category,
        String dateTime,
        VenueInput venue,
        List<SeatCategoryInput> seatCategories
) {
}

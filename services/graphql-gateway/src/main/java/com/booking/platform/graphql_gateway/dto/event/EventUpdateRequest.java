package com.booking.platform.graphql_gateway.dto.event;

import java.util.List;

/**
 * Internal DTO passed from EventResolver to EventClient for event updates.
 * All fields except eventId are optional — only non-null fields will be updated.
 */
public record EventUpdateRequest(
        String eventId,
        String title,
        String description,
        String category,
        String dateTime,
        VenueInput venue,
        List<SeatCategoryInput> seatCategories
) {
}

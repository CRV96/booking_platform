package com.booking.platform.graphql_gateway.dto.event;

import java.util.List;

/**
 * Internal DTO passed from EventResolver to EventClient for event creation.
 * Wraps all fields needed for a CreateEvent gRPC call into a single object.
 */
public record EventCreateRequest(
        String title,
        String description,
        String category,
        String dateTime,
        VenueInput venue,
        List<SeatCategoryInput> seatCategories
) {
}

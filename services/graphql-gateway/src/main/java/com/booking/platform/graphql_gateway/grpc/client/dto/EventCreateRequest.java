package com.booking.platform.graphql_gateway.grpc.client.dto;

import com.booking.platform.graphql_gateway.dto.event.SeatCategoryInput;
import com.booking.platform.graphql_gateway.dto.event.VenueInput;

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

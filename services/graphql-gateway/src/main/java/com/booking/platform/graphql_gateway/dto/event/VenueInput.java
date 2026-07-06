package com.booking.platform.graphql_gateway.dto.event;

/**
 * GraphQL input for creating or updating a venue.
 */
public record VenueInput(
        String name,
        String address,
        String city,
        String country,
        Double latitude,
        Double longitude,
        Integer capacity
) {
}

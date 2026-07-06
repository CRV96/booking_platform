package com.booking.platform.graphql_gateway.dto.event;

/**
 * GraphQL DTO representing venue information embedded in an event.
 */
public record VenueInfo(
        String name,
        String address,
        String city,
        String country,
        Double latitude,
        Double longitude,
        Integer capacity
) {
    public static VenueInfo fromGrpc(com.booking.platform.common.grpc.event.VenueInfo venueInfo) {
        return new VenueInfo(
                venueInfo.getName(),
                venueInfo.getAddress().isBlank() ? null : venueInfo.getAddress(),
                venueInfo.getCity(),
                venueInfo.getCountry(),
                venueInfo.hasLatitude() ? venueInfo.getLatitude() : null,
                venueInfo.hasLongitude() ? venueInfo.getLongitude() : null,
                venueInfo.hasCapacity() ? venueInfo.getCapacity() : null
        );
    }
}

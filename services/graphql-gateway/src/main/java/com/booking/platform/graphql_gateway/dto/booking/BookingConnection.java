package com.booking.platform.graphql_gateway.dto.booking;

import java.util.List;

/**
 * GraphQL paginated response for booking listings.
 */
public record BookingConnection(
        List<Booking> bookings,
        int totalCount,
        int page,
        int pageSize,
        int totalPages
) {
}

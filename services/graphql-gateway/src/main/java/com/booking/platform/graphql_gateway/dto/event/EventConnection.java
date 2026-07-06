package com.booking.platform.graphql_gateway.dto.event;

import java.util.List;

/**
 * GraphQL paginated response for event listings.
 */
public record EventConnection(
        List<Event> events,
        int totalCount,
        int page,
        int pageSize,
        int totalPages
) {
}

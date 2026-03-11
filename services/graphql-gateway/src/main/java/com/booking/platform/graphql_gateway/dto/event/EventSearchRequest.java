package com.booking.platform.graphql_gateway.dto.event;

/**
 * Internal DTO passed from EventResolver to EventClient for event search/listing.
 * All filter fields are optional — omitting them returns all published events.
 */
public record EventSearchRequest(
        String query,
        String category,
        String city,
        String dateFrom,
        String dateTo,
        int page,
        int pageSize
) {
}

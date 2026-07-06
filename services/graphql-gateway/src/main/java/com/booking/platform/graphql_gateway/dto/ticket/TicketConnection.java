package com.booking.platform.graphql_gateway.dto.ticket;

import java.util.List;

/**
 * GraphQL paginated response for ticket listings.
 */
public record TicketConnection(
        List<Ticket> tickets,
        int totalCount,
        int page,
        int pageSize,
        int totalPages
) {
}

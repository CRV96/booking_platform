package com.booking.platform.ticket_service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Ticket Service.
 */
@ConfigurationProperties(prefix = "ticket-service.ticket")
public record TicketProperties(
        int maxQuantityPerBooking
) {
    private static final int DEFAULT_MAX_QUANTITY = 20;

    public TicketProperties {
        if (maxQuantityPerBooking <= 0) maxQuantityPerBooking = DEFAULT_MAX_QUANTITY;
    }
}

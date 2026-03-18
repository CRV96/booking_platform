package com.booking.platform.ticket_service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Ticket Service.
 */
@ConfigurationProperties(prefix = "ticket-service.ticket")
public record TicketProperties
        (
                int maxQuantityPerBooking
        )
{
    // Default to 20 if not set or invalid
    public TicketProperties{
        if (maxQuantityPerBooking <= 0) maxQuantityPerBooking = 20;
    }
}

package com.booking.platform.event_service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Event Service.
 */
@ConfigurationProperties(prefix = "event-service")
public record EventProperties(
        Pagination pagination
) {
    public record Pagination(
            int defaultPageSize,
            int maxPageSize,
            long maxSkippableResults
    ) {
        public Pagination {
            if (defaultPageSize <= 0) defaultPageSize = 20;
            if (maxPageSize <= 0) maxPageSize = 100;
            if (maxSkippableResults <= 0) maxSkippableResults = 5000;
        }
    }

    public EventProperties {
        if (pagination == null) pagination = new Pagination(20, 100, 5000);
    }
}

package com.booking.platform.booking_service.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * General booking-service configuration properties.
 * Prefix: booking
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    private Pagination pagination = new Pagination();

    @Getter
    @Setter
    public static class Pagination {
        /** Default page size when none is specified (default 20). */
        private int defaultPageSize = 20;

        /** Maximum allowed page size to prevent excessive queries (default 100). */
        private int maxPageSize = 100;
    }
}

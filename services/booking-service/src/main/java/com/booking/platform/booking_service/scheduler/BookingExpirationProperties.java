package com.booking.platform.booking_service.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the booking expiration scheduler.
 * Prefix: booking.expiration
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "booking.expiration")
public class BookingExpirationProperties {

    /** Scheduler interval in milliseconds (default 30s). */
    private long interval = 30_000;

    /** TTL for the distributed scheduler lock (default 25s). Must be less than interval. */
    private Duration lockTtl = Duration.ofSeconds(25);

    /** Maximum number of expired bookings to process per scheduler tick. */
    private int batchSize = 100;
}

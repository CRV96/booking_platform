package com.booking.platform.booking_service.lock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the Redis distributed lock.
 * Prefix: booking.lock
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "booking.lock")
public class DistributedLockProperties {

    /** Time-to-live for the lock key. Auto-expires if JVM crashes. */
    private Duration ttl = Duration.ofSeconds(7);

    /** Maximum number of acquire attempts before giving up. */
    private int maxRetries = 3;

    /** Base delay between retries. Multiplied by attempt number for linear backoff. */
    private Duration retryDelay = Duration.ofMillis(100);
}

package com.booking.platform.booking_service.config;

import com.booking.platform.booking_service.scheduler.BookingExpirationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support and registers
 * the booking expiration configuration properties.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BookingExpirationProperties.class)
public class SchedulingConfig {
}

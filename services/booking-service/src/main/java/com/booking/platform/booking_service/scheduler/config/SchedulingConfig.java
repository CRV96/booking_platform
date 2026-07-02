package com.booking.platform.booking_service.scheduler.config;

import com.booking.platform.booking_service.properties.BookingExpirationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the booking expiration configuration properties.
 *
 * <p>{@code @EnableScheduling} and {@code @EnableSchedulerLock} are activated
 * by {@code ShedLockConfig} in common-core — no need to declare them here.
 */
@Configuration
@EnableConfigurationProperties(BookingExpirationProperties.class)
public class SchedulingConfig {
}

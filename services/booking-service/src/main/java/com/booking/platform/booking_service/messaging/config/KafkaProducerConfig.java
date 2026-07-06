package com.booking.platform.booking_service.messaging.config;

import com.booking.platform.common.events.config.BaseKafkaProducerConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer configuration for booking-service.
 *
 * <p>Booking-service publishes three event types:
 * {@code BookingCreatedEvent}, {@code BookingConfirmedEvent}, {@code BookingCancelledEvent}.
 * All producer settings are inherited from {@link BaseKafkaProducerConfig}.
 */
@Configuration
public class KafkaProducerConfig extends BaseKafkaProducerConfig {
    // All configuration inherited from BaseKafkaProducerConfig
}

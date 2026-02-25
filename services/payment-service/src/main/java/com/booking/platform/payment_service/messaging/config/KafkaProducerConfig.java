package com.booking.platform.payment_service.messaging.config;

import com.booking.platform.common.events.config.BaseKafkaProducerConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer configuration for payment-service.
 *
 * <p>Payment-service publishes {@code PaymentCompletedEvent} and {@code PaymentFailedEvent}.
 * All producer settings are inherited from {@link BaseKafkaProducerConfig}.
 */
@Configuration
public class KafkaProducerConfig extends BaseKafkaProducerConfig {
    // All configuration inherited from BaseKafkaProducerConfig
}

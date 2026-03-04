package com.booking.platform.analytics_service.messaging.config;

import com.booking.platform.common.events.config.BaseKafkaProducerConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer configuration for analytics-service.
 *
 * <p>Analytics-service is a pure consumer — it does not publish domain events.
 * This config exists solely to provide the {@code dltKafkaTemplate} bean
 * required by the {@link com.booking.platform.common.events.config.BaseKafkaConsumerConfig#errorHandler}
 * for dead-letter topic publishing.
 */
@Configuration
public class KafkaProducerConfig extends BaseKafkaProducerConfig {
    // All configuration inherited from BaseKafkaProducerConfig
}

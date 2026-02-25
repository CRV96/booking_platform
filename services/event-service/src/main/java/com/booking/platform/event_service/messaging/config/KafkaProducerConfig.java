package com.booking.platform.event_service.messaging.config;

import com.booking.platform.common.events.config.BaseKafkaProducerConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer configuration for event-service.
 *
 * <p>Event-service is a pure producer — it publishes event lifecycle events
 * (created, updated, published, cancelled) and does not consume from any topic.
 * All producer settings (serializers, reliability, batching) are inherited from
 * {@link BaseKafkaProducerConfig}.
 */
@Configuration
public class KafkaProducerConfig extends BaseKafkaProducerConfig {
    // All configuration inherited from BaseKafkaProducerConfig
}

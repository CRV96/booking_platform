package com.booking.platform.ticket_service.messaging.config;

import com.booking.platform.common.events.config.BaseKafkaProducerConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka producer configuration for ticket-service.
 *
 * <p>Ticket-service is primarily a consumer. The producer inherited from
 * {@link BaseKafkaProducerConfig} is used exclusively for forwarding failed
 * messages to Dead Letter Topics via the
 * {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}.
 */
@Configuration
public class KafkaProducerConfig extends BaseKafkaProducerConfig {
    // All configuration inherited from BaseKafkaProducerConfig
}

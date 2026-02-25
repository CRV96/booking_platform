package com.booking.platform.payment_service.messaging.config;

import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.config.BaseKafkaConsumerConfig;
import com.booking.platform.common.events.serialization.ProtobufDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

import java.util.Map;

/**
 * Kafka consumer configuration for payment-service.
 *
 * <p>Payment-service consumes from one topic:
 * <ul>
 *   <li>{@code events.booking.created} — triggers payment processing (stub: auto-success)</li>
 * </ul>
 *
 * <p>Base infrastructure (error handler, DLT, default factory) is inherited
 * from {@link BaseKafkaConsumerConfig}; only the typed event factory is defined here.
 */
@Configuration
public class KafkaConsumerConfig extends BaseKafkaConsumerConfig {

    // ── BookingCreatedEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, BookingCreatedEvent> bookingCreatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingCreatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingCreatedEvent> bookingCreatedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(bookingCreatedConsumerFactory(), errorHandler);
    }
}

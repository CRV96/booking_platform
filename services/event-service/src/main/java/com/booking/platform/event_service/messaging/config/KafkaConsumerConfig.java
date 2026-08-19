package com.booking.platform.event_service.messaging.config;

import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.config.BaseKafkaConsumerConfig;
import com.booking.platform.common.events.serialization.ProtobufDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;

import java.util.Map;

/**
 * Kafka consumer configuration for event-service — used only by semantic search.
 *
 * <p>Event-service is a producer for its own lifecycle events; here it also consumes
 * them (in its own consumer group) to keep the vector store in sync. The whole class
 * is gated on {@code app.semantic-search.enabled}, so with the feature off event-service
 * stays a pure producer and joins no consumer group.
 *
 * <p>Base infrastructure (error handler, retries → DLT, correlation-id interceptor) is
 * inherited from {@link BaseKafkaConsumerConfig}; only the typed factories live here.
 */
@Configuration
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class KafkaConsumerConfig extends BaseKafkaConsumerConfig {

    // ── EventCreatedEvent ─────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventCreatedEvent> eventCreatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventCreatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventCreatedEvent> eventCreatedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(eventCreatedConsumerFactory(), errorHandler);
    }

    // ── EventUpdatedEvent ─────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventUpdatedEvent> eventUpdatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventUpdatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventUpdatedEvent> eventUpdatedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(eventUpdatedConsumerFactory(), errorHandler);
    }

    // ── EventPublishedEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventPublishedEvent> eventPublishedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventPublishedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventPublishedEvent> eventPublishedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(eventPublishedConsumerFactory(), errorHandler);
    }

    // ── EventCancelledEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventCancelledEvent> eventCancelledConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventCancelledEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventCancelledEvent> eventCancelledListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(eventCancelledConsumerFactory(), errorHandler);
    }
}

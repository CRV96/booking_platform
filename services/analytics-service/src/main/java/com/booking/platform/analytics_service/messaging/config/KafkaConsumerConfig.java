package com.booking.platform.analytics_service.messaging.config;

import com.booking.platform.common.events.*;
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
 * Kafka consumer configuration for analytics-service.
 *
 * <p>Analytics-service consumes from all 10 domain topics:
 * <ul>
 *   <li>Event lifecycle:   created, updated, published, cancelled</li>
 *   <li>Booking lifecycle: created, confirmed, cancelled</li>
 *   <li>Payment lifecycle: completed, failed, refund-completed</li>
 * </ul>
 *
 * <p>Base infrastructure (error handler, DLT, default factory) is inherited
 * from {@link BaseKafkaConsumerConfig}; only the typed event factories are defined here.
 */
@Configuration
public class KafkaConsumerConfig extends BaseKafkaConsumerConfig {

    // ── Event domain ─────────────────────────────────────────────────────────

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

    // ── Booking domain ───────────────────────────────────────────────────────

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

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingConfirmedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> bookingConfirmedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(bookingConfirmedConsumerFactory(), errorHandler);
    }

    @Bean
    public ConsumerFactory<String, BookingCancelledEvent> bookingCancelledConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingCancelledEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingCancelledEvent> bookingCancelledListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(bookingCancelledConsumerFactory(), errorHandler);
    }

    // ── Payment domain ───────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, PaymentCompletedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(paymentCompletedConsumerFactory(), errorHandler);
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, PaymentFailedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(paymentFailedConsumerFactory(), errorHandler);
    }

    @Bean
    public ConsumerFactory<String, RefundCompletedEvent> refundCompletedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, RefundCompletedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RefundCompletedEvent> refundCompletedListenerFactory(
            CommonErrorHandler errorHandler) {
        return buildFactory(refundCompletedConsumerFactory(), errorHandler);
    }
}

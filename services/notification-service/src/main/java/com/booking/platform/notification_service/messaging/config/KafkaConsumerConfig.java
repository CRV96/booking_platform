package com.booking.platform.notification_service.messaging.config;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.common.events.PaymentFailedEvent;
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
 * Kafka consumer configuration for notification-service.
 *
 * <p>Notification-service consumes from 8 topics — all event and booking lifecycle
 * events, plus payment failure events. Base infrastructure (error handler, DLT,
 * default factory) is inherited from {@link BaseKafkaConsumerConfig}; only the
 * typed event factories are defined here.
 */
@Configuration
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

    // ── BookingConfirmedEvent ─────────────────────────────────────────────────

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

    // ── BookingCancelledEvent ─────────────────────────────────────────────────

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

    // ── PaymentFailedEvent (P3-07 compensation) ────────────────────────────────

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
}

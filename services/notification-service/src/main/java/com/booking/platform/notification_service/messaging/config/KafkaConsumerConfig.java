package com.booking.platform.notification_service.messaging.config;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.notification_service.messaging.deserializer.ProtobufDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer infrastructure for notification-service.
 *
 * <p>{@link EnableKafka} activates the {@code @KafkaListener} annotation processor.
 * This is normally done automatically by Spring Boot's {@code KafkaAutoConfiguration},
 * but because we define multiple typed {@link ConsumerFactory} beans (one per Protobuf
 * message type), we declare it explicitly here to keep full control.
 *
 * <p>The {@code kafkaConsumerFactory} bean is marked {@link Primary} so that Spring
 * Boot's auto-configured {@code kafkaListenerContainerFactory} has a single unambiguous
 * {@code ConsumerFactory<Object, Object>} to inject. Our typed listener methods each
 * reference their own named factory via {@code containerFactory} on {@code @KafkaListener},
 * bypassing the default factory entirely.
 *
 * <p>The listener containers are single-threaded ({@code concurrency=1}) which is
 * appropriate for dev. Increase concurrency in production to match the topic's
 * partition count for parallel consumption.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── Shared base config ────────────────────────────────────────────────────

    /**
     * Builds the common consumer settings shared across all factories.
     * Each factory adds its specific Protobuf parser on top of these.
     */
    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Start from the earliest unread message on first join.
        // Prevents silent message loss when the service restarts after being down.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufDeserializer.class);
        return config;
    }

    // ── Default factory (@Primary) ────────────────────────────────────────────
    // Required so Spring Boot's auto-configured kafkaListenerContainerFactory
    // has exactly one ConsumerFactory<Object, Object> to inject.
    // Our typed @KafkaListener methods never use this factory — they each
    // reference their own named factory via containerFactory = "...".

    @Primary
    @Bean
    public ConsumerFactory<Object, Object> kafkaConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    // ── EventCreatedEvent ─────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventCreatedEvent> eventCreatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventCreatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventCreatedEvent> eventCreatedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventCreatedConsumerFactory());
        return factory;
    }

    // ── EventUpdatedEvent ─────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventUpdatedEvent> eventUpdatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventUpdatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventUpdatedEvent> eventUpdatedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventUpdatedConsumerFactory());
        return factory;
    }

    // ── EventPublishedEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventPublishedEvent> eventPublishedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventPublishedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventPublishedEvent> eventPublishedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventPublishedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventPublishedConsumerFactory());
        return factory;
    }

    // ── EventCancelledEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, EventCancelledEvent> eventCancelledConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, EventCancelledEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventCancelledEvent> eventCancelledListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventCancelledConsumerFactory());
        return factory;
    }

    // ── BookingCreatedEvent ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, BookingCreatedEvent> bookingCreatedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingCreatedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingCreatedEvent> bookingCreatedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, BookingCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingCreatedConsumerFactory());
        return factory;
    }

    // ── BookingConfirmedEvent ─────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingConfirmedEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> bookingConfirmedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingConfirmedConsumerFactory());
        return factory;
    }

    // ── BookingCancelledEvent ─────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, BookingCancelledEvent> bookingCancelledConsumerFactory() {
        Map<String, Object> config = baseConfig();
        config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, BookingCancelledEvent.parser());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingCancelledEvent> bookingCancelledListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, BookingCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingCancelledConsumerFactory());
        return factory;
    }
}

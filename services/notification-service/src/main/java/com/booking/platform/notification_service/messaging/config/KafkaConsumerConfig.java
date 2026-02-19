package com.booking.platform.notification_service.messaging.config;

import com.booking.platform.common.events.BookingCancelledEvent;
import com.booking.platform.common.events.BookingConfirmedEvent;
import com.booking.platform.common.events.BookingCreatedEvent;
import com.booking.platform.common.events.EventCancelledEvent;
import com.booking.platform.common.events.EventCreatedEvent;
import com.booking.platform.common.events.EventPublishedEvent;
import com.booking.platform.common.events.EventUpdatedEvent;
import com.booking.platform.notification_service.messaging.deserializer.ProtobufDeserializer;
import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

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
 * <h2>Error handling strategy</h2>
 * <p>Every listener container factory is configured with the same {@link DefaultErrorHandler}:
 * <ul>
 *   <li><b>Retry</b>: up to 3 attempts with exponential backoff (1s → 2s → 4s).</li>
 *   <li><b>Dead Letter Topic (DLT)</b>: after all retries are exhausted, the failed
 *       message is forwarded to {@code <original-topic>.DLT} via
 *       {@link DeadLetterPublishingRecoverer}. The DLT consumer logs it with full context.</li>
 *   <li><b>Poison pills</b>: deserialization failures (malformed Protobuf bytes) are
 *       caught by {@link ErrorHandlingDeserializer} and forwarded directly to the DLT
 *       without retrying — retrying an unreadable message is pointless.</li>
 * </ul>
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

    // ── Error handler ─────────────────────────────────────────────────────────

    /**
     * Shared error handler applied to every listener container factory.
     *
     * <p>On failure the handler retries up to 3 times with exponential backoff
     * (1 s → 2 s → 4 s). After all attempts fail, the message is forwarded to
     * the Dead Letter Topic ({@code <original-topic>.DLT}) using a
     * {@link DeadLetterPublishingRecoverer} backed by the injected
     * {@link KafkaTemplate}.
     *
     * <p>The {@code dltKafkaTemplate} (from {@link KafkaProducerConfig}) is injected
     * by name rather than using the auto-configured template. Spring Boot's default
     * template uses {@link org.apache.kafka.common.serialization.StringSerializer} for
     * values, which cannot serialize Protobuf objects and would throw a
     * {@code SerializationException} when the recoverer tries to publish a failed
     * message. The dedicated template uses {@link
     * com.booking.platform.notification_service.messaging.serializer.ProtobufSerializer}
     * which calls {@link MessageLite#toByteArray()} — matching the {@code byte[]} format
     * expected by the DLT consumer.
     *
     * @param dltKafkaTemplate Protobuf-backed template from {@link KafkaProducerConfig};
     *                         used by the DLT recoverer to publish failed messages
     */
    @Bean
    public CommonErrorHandler errorHandler(@Qualifier("dltKafkaTemplate") KafkaTemplate<String, MessageLite> dltKafkaTemplate) {
        // Exponential backoff: initial=1s, multiplier=2, max retries=3
        // Retry timeline: attempt 1 (immediate) → wait 1s → attempt 2 → wait 2s → attempt 3 → wait 4s → DLT
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        // After retries exhausted → publish to <topic>-dlt.
        // DeadLetterPublishingRecoverer defaults to "<original-topic>-dlt" naming,
        // which matches the topics pre-created in KafkaTopicConfig.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * Applies the shared {@link CommonErrorHandler} to the given factory.
     * Called by every typed listener factory builder to keep config DRY.
     */
    private <K, V> ConcurrentKafkaListenerContainerFactory<K, V> buildFactory(
            ConsumerFactory<K, V> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<K, V> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
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

    // ── DLT factory ───────────────────────────────────────────────────────────

    /**
     * Consumer factory for Dead Letter Topic listeners.
     *
     * <p>DLT messages are consumed as raw {@code byte[]} because they may be
     * malformed Protobuf (poison pills) that cannot be deserialized. Using
     * {@link ByteArrayDeserializer} ensures the DLT consumer always starts
     * successfully regardless of what bytes are in the topic.
     *
     * <p>This factory uses the {@code notification-service-dlt-group} consumer
     * group (defined inline on each {@code @KafkaListener} in {@link
     * com.booking.platform.notification_service.messaging.consumer.DltConsumer}),
     * keeping DLT consumption independent of main topic consumption.
     */
    @Bean
    public ConsumerFactory<String, byte[]> dltConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> dltListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dltConsumerFactory());
        // No error handler on the DLT factory — if the DLT consumer itself fails,
        // we log and move on rather than creating a DLT of a DLT.
        return factory;
    }
}

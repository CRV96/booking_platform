package com.booking.platform.common.events.config;

import com.booking.platform.common.events.serialization.ProtobufDeserializer;
import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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
 * Base Kafka consumer configuration shared across all consuming services.
 *
 * <p>Provides the common infrastructure that every consumer needs:
 * <ul>
 *   <li>{@link #baseConfig()} — shared consumer settings (bootstrap, group-id, deserializers)</li>
 *   <li>{@link #errorHandler} — 3 retries with exponential backoff (1s→2s→4s), then DLT</li>
 *   <li>{@link #buildFactory} — helper to build typed listener container factories</li>
 *   <li>{@link #kafkaConsumerFactory()} — default {@code @Primary} factory for Spring Boot</li>
 *   <li>{@link #dltConsumerFactory()} + {@link #dltListenerFactory()} — DLT consumption as raw bytes</li>
 * </ul>
 *
 * <p>Services extend this class, annotate with {@code @Configuration}, and add
 * only their event-specific typed consumer factories. Example:
 * <pre>
 * {@code @Configuration}
 * public class KafkaConsumerConfig extends BaseKafkaConsumerConfig {
 *
 *     {@code @Bean}
 *     public ConsumerFactory<String, PaymentCompletedEvent> paymentCompletedConsumerFactory() {
 *         Map<String, Object> config = baseConfig();
 *         config.put(ProtobufDeserializer.PARSER_CONFIG_KEY, PaymentCompletedEvent.parser());
 *         return new DefaultKafkaConsumerFactory<>(config);
 *     }
 *
 *     {@code @Bean}
 *     public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent>
 *             paymentCompletedListenerFactory(CommonErrorHandler errorHandler) {
 *         return buildFactory(paymentCompletedConsumerFactory(), errorHandler);
 *     }
 * }
 * </pre>
 */
@EnableKafka
public abstract class BaseKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ── Shared base config ────────────────────────────────────────────────────

    /**
     * Builds the common consumer settings shared across all typed factories.
     * Each subclass factory adds its specific Protobuf parser on top of these.
     */
    protected Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufDeserializer.class);
        return config;
    }

    // ── Error handler ─────────────────────────────────────────────────────────

    /**
     * Shared error handler: 3 retries with exponential backoff (1s → 2s → 4s),
     * then forward to Dead Letter Topic via {@link DeadLetterPublishingRecoverer}.
     */
    @Bean
    public CommonErrorHandler errorHandler(
            @Qualifier("dltKafkaTemplate") KafkaTemplate<String, MessageLite> dltKafkaTemplate) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    // ── Factory builder ───────────────────────────────────────────────────────

    /**
     * Builds a typed {@link ConcurrentKafkaListenerContainerFactory} with the
     * shared error handler. Called by subclass factory methods.
     */
    protected <K, V> ConcurrentKafkaListenerContainerFactory<K, V> buildFactory(
            ConsumerFactory<K, V> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<K, V> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ── Default factory (@Primary) ────────────────────────────────────────────

    /**
     * Default consumer factory required by Spring Boot auto-configuration.
     * Our typed {@code @KafkaListener} methods each reference their own named
     * factory via {@code containerFactory = "..."}, bypassing this one.
     */
    @Primary
    @Bean
    public ConsumerFactory<Object, Object> kafkaConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConfig());
    }

    // ── DLT factory ───────────────────────────────────────────────────────────

    /**
     * DLT consumer factory. Messages are consumed as raw {@code byte[]} because
     * they may be malformed Protobuf (poison pills) that cannot be deserialized.
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
        return factory;
    }
}

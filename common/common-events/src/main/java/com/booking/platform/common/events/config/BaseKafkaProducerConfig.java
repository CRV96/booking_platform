package com.booking.platform.common.events.config;

import com.booking.platform.common.events.interceptor.CorrelationIdKafkaProducerInterceptor;
import com.booking.platform.common.events.serialization.ProtobufSerializer;
import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Base Kafka producer configuration shared across all services.
 *
 * <p>Provides a fully configured {@link ProducerFactory} and two {@link KafkaTemplate}
 * beans: a {@code @Primary} main template for publishing domain events and a dedicated
 * {@code dltKafkaTemplate} for the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}.
 *
 * <p>Services extend this class and annotate with {@code @Configuration}. If a service
 * only needs DLT publishing (e.g. notification-service, ticket-service), the subclass
 * body can be empty — the base factory works for both full and DLT-only producers.
 *
 * <p>To customise producer settings, override {@link #producerFactory()} in the
 * subclass and call {@code super.producerFactory()} or build a new one.
 *
 * <h2>Default settings</h2>
 * <ul>
 *   <li>Key serializer: {@link StringSerializer} (entity ID as key)</li>
 *   <li>Value serializer: {@link ProtobufSerializer} (binary Protobuf)</li>
 *   <li>acks=1, retries=3, snappy compression, 5ms linger</li>
 * </ul>
 */
public abstract class BaseKafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, MessageLite> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // ── Connectivity ──────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ── Serializers ───────────────────────────────────────────────
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);

        // ── Reliability ───────────────────────────────────────────────
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);

        // ── Throughput / Batching ─────────────────────────────────────
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33_554_432);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60_000);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // ── Ordering ──────────────────────────────────────────────────
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // ── Correlation ID propagation ───────────────────────────────
        config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                CorrelationIdKafkaProducerInterceptor.class.getName());

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Primary {@link KafkaTemplate} for publishing domain events.
     * Injected by default wherever {@code KafkaTemplate<String, MessageLite>}
     * is autowired (e.g. event publishers).
     */
    @Primary
    @Bean
    public KafkaTemplate<String, MessageLite> kafkaTemplate(
            ProducerFactory<String, MessageLite> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Dedicated template for the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}.
     * Injected by name ({@code @Qualifier("dltKafkaTemplate")}) in
     * {@link BaseKafkaConsumerConfig#errorHandler}.
     */
    @Bean
    public KafkaTemplate<String, MessageLite> dltKafkaTemplate(
            ProducerFactory<String, MessageLite> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

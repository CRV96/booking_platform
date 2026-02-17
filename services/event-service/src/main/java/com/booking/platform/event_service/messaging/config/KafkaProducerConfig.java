package com.booking.platform.event_service.messaging.config;

import com.booking.platform.event_service.messaging.serializer.ProtobufSerializer;
import com.google.protobuf.MessageLite;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer infrastructure for event-service.
 *
 * <p>Defines a single {@link KafkaTemplate} typed to {@code <String, MessageLite>}:
 * <ul>
 *   <li><b>Key</b>   — {@code String} (event/booking/payment ID). Using the entity ID
 *       as the key ensures all events for the same entity land on the same partition,
 *       preserving ordering per entity across topics.</li>
 *   <li><b>Value</b> — {@link MessageLite} (any generated Protobuf message). The
 *       {@link ProtobufSerializer} converts it to binary bytes on the wire.</li>
 * </ul>
 *
 * <p>Producer settings (acks, retries, batch size, linger) are driven by properties
 * in {@code config/dev/event-service.properties} and are already bound by Spring Boot
 * auto-configuration before this bean is created. The explicit {@link ProducerConfig}
 * entries here only set the bootstrap servers and the serializer classes — everything
 * else inherits from {@code spring.kafka.producer.*}.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, MessageLite> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // ── CONNECTIVITY ──────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // The Kafka broker(s) to connect to. Can be multiple: "host1:9092,host2:9092"
        // Kafka client only needs ONE to bootstrap — it discovers the rest automatically

        // ── SERIALIZERS ───────────────────────────────────────────────
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);

        // ── RELIABILITY ───────────────────────────────────────────────
        config.put(ProducerConfig.ACKS_CONFIG, "1");
        // "0" = fire and forget, no confirmation at all (fastest, can lose data)
        // "1" = leader partition confirms receipt (default, good balance)
        // "all" = leader + ALL replicas confirm (slowest, zero data loss)

        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        // How many times to retry on transient failures (network blip, broker restart)

        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        // Wait 100ms between retries (avoids hammering a struggling broker)

        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        // Total time budget for a message to be delivered (retries included)
        // If exceeded → the CompletableFuture completes exceptionally

        // ── THROUGHPUT / BATCHING ─────────────────────────────────────
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);
        // Max bytes to collect in a batch before sending (16 KB default)
        // Larger batch = fewer network calls = higher throughput

        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        // Wait up to 5ms to fill the batch before sending
        // linger=0 → send immediately (low latency, small batches)
        // linger=5 → tiny delay, much better throughput under load

        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33_554_432);
        // Total memory (32 MB) the producer uses to buffer unsent messages
        // If full → send() blocks for MAX_BLOCK_MS_CONFIG then throws

        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60_000);
        // How long send() blocks if buffer is full before throwing BufferExhaustedException

        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        // Compress batches before sending: none / gzip / snappy / lz4 / zstd
        // snappy = good balance of speed vs compression ratio

        // ── ORDERING ──────────────────────────────────────────────────
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // How many unacknowledged requests can be in-flight at once
        // If retries > 0 AND this > 1 → possible reordering on retry!
        // Set to 1 to guarantee strict ordering (at cost of throughput)

        // NOTE: ENABLE_IDEMPOTENCE_CONFIG is intentionally omitted (defaults to false).
        // Idempotence requires acks=all + retries>0 + max.in.flight<=5.
        // Enable it together with acks=all when moving to production.

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, MessageLite> kafkaTemplate(
            ProducerFactory<String, MessageLite> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

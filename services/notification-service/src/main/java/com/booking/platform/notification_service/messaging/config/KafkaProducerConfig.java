package com.booking.platform.notification_service.messaging.config;

import com.booking.platform.notification_service.messaging.serializer.ProtobufSerializer;
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
 * Kafka producer infrastructure for notification-service.
 *
 * <p>Notification-service is primarily a consumer service — it does not publish
 * domain events. However, it needs a producer to forward failed messages to their
 * Dead Letter Topics (DLTs) via the
 * {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer} configured
 * in {@link KafkaConsumerConfig}.
 *
 * <h2>Why a dedicated producer factory?</h2>
 * <p>Spring Boot's auto-configured {@code KafkaTemplate} uses {@link StringSerializer}
 * for values. The DLT recoverer receives a deserialized Protobuf object (e.g.
 * {@code EventCancelledEvent}) from the failed {@code ConsumerRecord} and tries to
 * re-publish it — {@code StringSerializer} cannot handle this and throws a
 * {@code SerializationException}, preventing DLT routing entirely.
 *
 * <p>This config exposes a {@code KafkaTemplate<String, MessageLite>} backed by
 * {@link ProtobufSerializer}, which calls {@link MessageLite#toByteArray()} on the
 * failed Protobuf object. The resulting bytes are then stored in the DLT, where the
 * {@link com.booking.platform.notification_service.messaging.consumer.DltConsumer}
 * reads them as {@code byte[]} — the types pair correctly end-to-end.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Producer factory that serializes Protobuf messages to binary wire format.
     * Used exclusively by the DLT {@link KafkaTemplate} below.
     */
    @Bean
    public ProducerFactory<String, MessageLite> dltProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate backed by {@link ProtobufSerializer}.
     *
     * <p>Injected by name ({@code "dltKafkaTemplate"}) into
     * {@link KafkaConsumerConfig#errorHandler} to give the
     * {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer} a
     * serializer that can handle Protobuf objects.
     */
    @Bean
    public KafkaTemplate<String, MessageLite> dltKafkaTemplate() {
        return new KafkaTemplate<>(dltProducerFactory());
    }
}

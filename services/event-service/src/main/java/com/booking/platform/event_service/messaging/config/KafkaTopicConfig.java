package com.booking.platform.event_service.messaging.config;

import com.booking.platform.common.events.KafkaTopics;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

/**
 * Pre-creates the event-service Kafka topics on startup.
 *
 * <p>Without this, topics are auto-created by the broker the first time a
 * producer sends to them — using the broker's default settings
 * ({@code num.partitions}, {@code default.replication.factor}). Pre-creating
 * them here gives us explicit control over partition count and replication
 * factor, and eliminates the one-time {@code UNKNOWN_TOPIC_OR_PARTITION}
 * warning on first publish.
 *
 * <p>Only event-service topics are declared here. Topics owned by other
 * services (booking, payment) are the responsibility of those services and
 * will be created by the broker when those services come online.
 *
 * <p>Partition counts are set to 3 for dev (matches broker default).
 * In production, tune per topic based on expected throughput and consumer count.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    // ── Event lifecycle topics ────────────────────────────────────────────────

    @Bean
    public NewTopic eventCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.EVENT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic eventUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.EVENT_UPDATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic eventPublishedTopic() {
        return TopicBuilder.name(KafkaTopics.EVENT_PUBLISHED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic eventCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.EVENT_CANCELLED).partitions(3).replicas(1).build();
    }
}

package com.booking.platform.notification_service.messaging.config;

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
 * Pre-creates the Dead Letter Topics (DLTs) owned by notification-service.
 *
 * <p>Each main topic consumed by this service has a corresponding DLT named
 * {@code <topic>-dlt}. The {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
 * configured in {@link KafkaConsumerConfig} publishes failed messages there
 * after all retry attempts are exhausted. The {@code -dlt} suffix (hyphen, lowercase)
 * is the default naming convention used by {@code DeadLetterPublishingRecoverer}
 * when combined with {@code DefaultErrorHandler}.
 *
 * <p>DLTs are declared here explicitly so they exist with controlled settings
 * (1 partition, 1 replica for dev) before any failure occurs. Without this,
 * Kafka would auto-create them with broker defaults on first failure.
 *
 * <p>DLTs use a single partition — ordering within the DLT is not a concern
 * since these are already failed messages awaiting manual investigation.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin notificationKafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    // ── Event DLTs ────────────────────────────────────────────────────────────

    @Bean
    public NewTopic eventCreatedDlt() {
        return TopicBuilder.name(KafkaTopics.EVENT_CREATED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic eventUpdatedDlt() {
        return TopicBuilder.name(KafkaTopics.EVENT_UPDATED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic eventPublishedDlt() {
        return TopicBuilder.name(KafkaTopics.EVENT_PUBLISHED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic eventCancelledDlt() {
        return TopicBuilder.name(KafkaTopics.EVENT_CANCELLED + "-dlt").partitions(1).replicas(1).build();
    }

    // ── Booking DLTs ──────────────────────────────────────────────────────────

    @Bean
    public NewTopic bookingCreatedDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CREATED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bookingConfirmedDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CONFIRMED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic bookingCancelledDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CANCELLED + "-dlt").partitions(1).replicas(1).build();
    }

    // ── Payment DLTs ───────────────────────────────────────────────────────────

    @Bean
    public NewTopic paymentFailedDlt() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED + "-dlt").partitions(1).replicas(1).build();
    }
}

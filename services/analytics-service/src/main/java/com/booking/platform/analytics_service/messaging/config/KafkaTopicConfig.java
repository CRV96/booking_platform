package com.booking.platform.analytics_service.messaging.config;

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
 * Pre-creates DLT topics for all 10 consumed topics.
 *
 * <p>Analytics-service does not own any producer topics — it only consumes.
 * DLT topics (1 partition each) are created so that poison-pill messages
 * can be forwarded by the {@link com.booking.platform.common.events.config.BaseKafkaConsumerConfig#errorHandler}.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin analyticsKafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    // ── Event domain DLTs ────────────────────────────────────────────────────

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

    // ── Booking domain DLTs ──────────────────────────────────────────────────

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

    // ── Payment domain DLTs ──────────────────────────────────────────────────

    @Bean
    public NewTopic paymentCompletedDlt() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedDlt() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentRefundCompletedDlt() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_REFUND_COMPLETED + "-dlt").partitions(1).replicas(1).build();
    }
}

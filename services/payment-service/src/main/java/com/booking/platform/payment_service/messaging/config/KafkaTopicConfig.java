package com.booking.platform.payment_service.messaging.config;

import com.booking.platform.common.events.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Pre-creates Kafka topics owned or consumed by payment-service.
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final int DLT_PARTITIONS = 1;
    private static final int REPLICAS = 1;

    // ── Payment lifecycle topics (produced by payment-service) ────────────────

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic paymentRefundCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_REFUND_COMPLETED)
                .partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    // ── DLT topics (consumed booking topics) ─────────────────────────────────

    @Bean
    public NewTopic bookingCreatedDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CREATED + "-dlt")
                .partitions(DLT_PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic bookingCancelledDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CANCELLED + "-dlt")
                .partitions(DLT_PARTITIONS).replicas(REPLICAS).build();
    }
}

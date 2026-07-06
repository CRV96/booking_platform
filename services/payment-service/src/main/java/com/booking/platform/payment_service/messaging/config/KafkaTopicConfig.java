package com.booking.platform.payment_service.messaging.config;

import com.booking.platform.common.events.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Pre-creates Kafka topics owned or consumed by payment-service.
 *
 * <p>Partition count is configurable via {@code kafka.topic.partitions} (default: 3).
 * DLT topics always use 1 partition — they receive low volume and need strict ordering.
 */
@Configuration
public class KafkaTopicConfig {

    private static final String DLT_SUFFIX = "-dlt";
    private static final int DLT_PARTITIONS = 1;
    private static final int REPLICAS = 1;

    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    // ── Payment lifecycle topics (produced by payment-service) ────────────────

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(partitions).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(partitions).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic paymentRefundCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_REFUND_COMPLETED)
                .partitions(partitions).replicas(REPLICAS).build();
    }

    // ── DLT topics (consumed booking topics) ─────────────────────────────────

    @Bean
    public NewTopic bookingCreatedDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CREATED + DLT_SUFFIX)
                .partitions(DLT_PARTITIONS).replicas(REPLICAS).build();
    }

    @Bean
    public NewTopic bookingCancelledDlt() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CANCELLED + DLT_SUFFIX)
                .partitions(DLT_PARTITIONS).replicas(REPLICAS).build();
    }
}

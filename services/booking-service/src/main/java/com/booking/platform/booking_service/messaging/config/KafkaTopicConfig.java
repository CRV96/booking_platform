package com.booking.platform.booking_service.messaging.config;

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
 * Pre-creates the Kafka topics owned by booking-service on startup.
 *
 * <p>Booking-service owns three producer topics (booking lifecycle) and consumes
 * from one payment topic. This config creates:
 * <ul>
 *   <li><b>Producer topics</b> (3 partitions each):
 *       {@code events.booking.created}, {@code events.booking.confirmed},
 *       {@code events.booking.cancelled}</li>
 *   <li><b>DLT topics</b> (1 partition each):
 *       {@code events.payment.completed-dlt} — for failed payment events</li>
 * </ul>
 *
 * <p>Partition counts are set to 3 for dev (matches broker default).
 * In production, tune per topic based on expected throughput and consumer count.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin bookingKafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    // ── Booking producer topics ───────────────────────────────────────────────

    @Bean
    public NewTopic bookingCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CONFIRMED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic bookingCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.BOOKING_CANCELLED).partitions(3).replicas(1).build();
    }

    // ── DLT for consumed payment topic ────────────────────────────────────────

    @Bean
    public NewTopic paymentCompletedDlt() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED + "-dlt").partitions(1).replicas(1).build();
    }
}

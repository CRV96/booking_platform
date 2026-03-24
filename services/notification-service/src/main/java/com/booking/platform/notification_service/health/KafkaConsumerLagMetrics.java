package com.booking.platform.notification_service.health;

import com.booking.platform.common.events.KafkaTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer {@link MeterBinder} that exposes Kafka consumer lag as gauges.
 *
 * <p>Consumer lag = how many messages are waiting in a topic partition that
 * the consumer group has not yet processed. A lag of 0 means the consumer
 * is caught up. A growing lag means the consumer is falling behind the producer.
 *
 * <h2>Metric name</h2>
 * <pre>kafka.consumer.lag{topic="events.event.cancelled", partition="0", group="notification-service-group"}</pre>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>For each topic+partition, fetch the latest offset (end of the log) from the broker.</li>
 *   <li>Fetch the committed offset for our consumer group on that partition.</li>
 *   <li>Lag = latest offset − committed offset.</li>
 * </ol>
 *
 * <p>Gauges are re-evaluated every time Micrometer scrapes the metric (every
 * {@code management.metrics.export.simple.step} interval, default 1 minute).
 * Each scrape opens a short-lived {@link AdminClient} connection.
 *
 * <p>Visible at {@code /actuator/metrics/kafka.consumer.lag} or in any
 * Micrometer-compatible backend (Prometheus, Grafana, etc).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumerLagMetrics implements MeterBinder {

    @Value("${spring.kafka.consumer.group-id:notification-service-group}")
    private static String CONSUMER_GROUP;

    private static final String METRIC_NAME        = "kafka.consumer.lag";
    private static final String METRIC_DESCRIPTION = "Number of messages the consumer group is behind the latest offset";
    private static final int    TIMEOUT_SECONDS    = 5;

    private static final List<String> MONITORED_TOPICS = List.of(
            KafkaTopics.EVENT_CREATED,
            KafkaTopics.EVENT_UPDATED,
            KafkaTopics.EVENT_PUBLISHED,
            KafkaTopics.EVENT_CANCELLED,
            KafkaTopics.BOOKING_CREATED,
            KafkaTopics.BOOKING_CONFIRMED,
            KafkaTopics.BOOKING_CANCELLED
    );

    private final KafkaAdmin kafkaAdmin;

    /**
     * Called once by Micrometer on startup to register all lag gauges.
     * Each gauge is a lazy supplier — the AdminClient call only happens when
     * Micrometer scrapes the value, not at registration time.
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        for (String topic : MONITORED_TOPICS) {
            // Topics have 3 partitions (configured in KafkaTopicConfig)
            for (int partition = 0; partition < 3; partition++) {
                final int p = partition;
                Gauge.builder(METRIC_NAME, this, m -> m.fetchLag(topic, p))
                        .description(METRIC_DESCRIPTION)
                        .tag("topic", topic)
                        .tag("partition", String.valueOf(partition))
                        .tag("group", CONSUMER_GROUP)
                        .register(registry);
            }
        }
    }

    /**
     * Fetches the current lag for a specific topic+partition by comparing
     * the latest broker offset against the consumer group's committed offset.
     *
     * @return lag value (≥ 0), or -1 if the value cannot be determined
     */
    private double fetchLag(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            // Latest offset at the end of the partition log
            Map<TopicPartition, OffsetSpec> latestRequest = Map.of(topicPartition, OffsetSpec.latest());
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    adminClient.listOffsets(latestRequest)
                               .all()
                               .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Committed offset for our consumer group on this partition
            Map<TopicPartition, OffsetAndMetadata> committedOffsets =
                    adminClient.listConsumerGroupOffsets(CONSUMER_GROUP)
                               .partitionsToOffsetAndMetadata()
                               .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            ListOffsetsResult.ListOffsetsResultInfo latestInfo = latestOffsets.get(topicPartition);
            OffsetAndMetadata committed = committedOffsets.get(topicPartition);

            if (latestInfo == null) {
                return -1;
            }

            long latestOffset    = latestInfo.offset();
            // If no committed offset yet (group never consumed), treat as 0 committed
            long committedOffset = committed != null ? committed.offset() : 0;
            long lag             = Math.max(0, latestOffset - committedOffset);

            log.debug("[KAFKA_LAG] topic='{}', partition={}, latest={}, committed={}, lag={}",
                    topic, partition, latestOffset, committedOffset, lag);

            return lag;

        } catch (Exception e) {
            log.warn("[KAFKA_LAG] Failed to fetch lag for topic='{}', partition={}: {}",
                    topic, partition, e.getMessage());
            return -1;
        }
    }
}

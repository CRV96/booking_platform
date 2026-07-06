package com.booking.platform.notification_service.health;

import com.booking.platform.common.events.KafkaTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

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
public class KafkaConsumerLagMetrics implements MeterBinder {

    private static final String METRIC_NAME        = "kafka.consumer.lag";
    private static final String METRIC_DESCRIPTION = "Number of messages the consumer group is behind the latest offset";
    private static final int    TIMEOUT_SECONDS    = 5;

    @Value("${notification.kafka.consumer-lag:false}")
    private boolean isConsumerLagEnabled;

    private static final List<String> MONITORED_TOPICS = List.of(
            KafkaTopics.EVENT_CREATED,
            KafkaTopics.EVENT_UPDATED,
            KafkaTopics.EVENT_PUBLISHED,
            KafkaTopics.EVENT_CANCELLED,
            KafkaTopics.BOOKING_CREATED,
            KafkaTopics.BOOKING_CONFIRMED,
            KafkaTopics.BOOKING_CANCELLED,
            KafkaTopics.PAYMENT_FAILED
    );

    private final KafkaAdmin kafkaAdmin;
    private final String consumerGroup;
    private final int partitionCount;

    public KafkaConsumerLagMetrics(
            KafkaAdmin kafkaAdmin,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${notification.kafka.partition-count:3}") int partitionCount) {
        this.kafkaAdmin = kafkaAdmin;
        this.consumerGroup = consumerGroup;
        this.partitionCount = partitionCount;
    }

    /**
     * Called once by Micrometer on startup to register all lag gauges.
     * Each gauge is a lazy supplier — the AdminClient call only happens when
     * Micrometer scrapes the value, not at registration time.
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        for (String topic : MONITORED_TOPICS) {
            for (int partition = 0; partition < partitionCount; partition++) {
                final int p = partition;
                Gauge.builder(METRIC_NAME, this, m -> m.fetchLag(topic, p))
                        .description(METRIC_DESCRIPTION)
                        .tag("topic", topic)
                        .tag("partition", String.valueOf(partition))
                        .tag("group", consumerGroup)
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
                    adminClient.listConsumerGroupOffsets(consumerGroup)
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

            if(isConsumerLagEnabled) {
                ApplicationLogger.logMessage(log, Level.DEBUG, "[KAFKA_LAG] topic='{}', partition={}, latest={}, committed={}, lag={}",
                        topic, partition, latestOffset, committedOffset, lag);
            }

            return lag;

        } catch (Exception e) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.NOTIFICATION_CONSUMER_ERROR,
                    "[KAFKA_LAG] Failed to fetch lag for topic='{}', partition={}: {}",
                    topic, partition, e.getMessage());
            return -1;
        }
    }
}

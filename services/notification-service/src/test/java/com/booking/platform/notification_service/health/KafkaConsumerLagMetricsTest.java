package com.booking.platform.notification_service.health;

import com.booking.platform.common.events.KafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerLagMetricsTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Test
    void bindTo_registersGaugesForAllTopicsAndPartitions() {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 1);
        MeterRegistry registry = new SimpleMeterRegistry();

        metrics.bindTo(registry);

        // 8 monitored topics × 1 partition = 8 gauges
        assertThat(registry.getMeters()).hasSize(8);
    }

    @Test
    void bindTo_registersGaugesWithPartitionCount2() {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 2);
        MeterRegistry registry = new SimpleMeterRegistry();

        metrics.bindTo(registry);

        // 8 monitored topics × 2 partitions = 16 gauges
        assertThat(registry.getMeters()).hasSize(16);
    }

    @Test
    void fetchLag_returnsCorrectLag() throws Exception {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 1);
        MeterRegistry registry = new SimpleMeterRegistry();

        TopicPartition topicPartition = new TopicPartition(KafkaTopics.EVENT_CREATED, 0);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            // Latest offset = 100
            ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> latestFuture = mock(KafkaFuture.class);
            when(adminClient.listOffsets(any())).thenReturn(listOffsetsResult);
            when(listOffsetsResult.all()).thenReturn(latestFuture);
            ListOffsetsResult.ListOffsetsResultInfo info = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
            when(info.offset()).thenReturn(100L);
            when(latestFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(Map.of(topicPartition, info));

            // Committed offset = 80
            ListConsumerGroupOffsetsResult offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> offsetsFuture = mock(KafkaFuture.class);
            when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(offsetsResult);
            when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(offsetsFuture);
            Map<TopicPartition, OffsetAndMetadata> committedMap = Map.of(
                    topicPartition, new OffsetAndMetadata(80));
            when(offsetsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(committedMap);

            metrics.bindTo(registry);

            double lag = registry.get("kafka.consumer.lag")
                    .tag("topic", KafkaTopics.EVENT_CREATED)
                    .tag("partition", "0")
                    .tag("group", "test-group")
                    .gauge()
                    .value();

            assertThat(lag).isEqualTo(20.0);
        }
    }

    @Test
    void fetchLag_returnsZeroWhenNoCommittedOffset() throws Exception {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 1);
        MeterRegistry registry = new SimpleMeterRegistry();

        TopicPartition topicPartition = new TopicPartition(KafkaTopics.EVENT_CREATED, 0);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            // Latest offset = 50
            ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> latestFuture = mock(KafkaFuture.class);
            when(adminClient.listOffsets(any())).thenReturn(listOffsetsResult);
            when(listOffsetsResult.all()).thenReturn(latestFuture);
            ListOffsetsResult.ListOffsetsResultInfo info = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
            when(info.offset()).thenReturn(50L);
            when(latestFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(Map.of(topicPartition, info));

            // No committed offset for this partition
            ListConsumerGroupOffsetsResult offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> offsetsFuture = mock(KafkaFuture.class);
            when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(offsetsResult);
            when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(offsetsFuture);
            when(offsetsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(Map.of());

            metrics.bindTo(registry);

            double lag = registry.get("kafka.consumer.lag")
                    .tag("topic", KafkaTopics.EVENT_CREATED)
                    .tag("partition", "0")
                    .tag("group", "test-group")
                    .gauge()
                    .value();

            // latest=50, committed=0 (default) → lag=50
            assertThat(lag).isEqualTo(50.0);
        }
    }

    @Test
    void fetchLag_returnsNegativeOneOnException() throws Exception {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 1);
        MeterRegistry registry = new SimpleMeterRegistry();

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            // listOffsets throws
            ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> latestFuture = mock(KafkaFuture.class);
            when(adminClient.listOffsets(any())).thenReturn(listOffsetsResult);
            when(listOffsetsResult.all()).thenReturn(latestFuture);
            when(latestFuture.get(anyLong(), any(TimeUnit.class)))
                    .thenThrow(new RuntimeException("broker unavailable"));

            metrics.bindTo(registry);

            double lag = registry.get("kafka.consumer.lag")
                    .tag("topic", KafkaTopics.EVENT_CREATED)
                    .tag("partition", "0")
                    .tag("group", "test-group")
                    .gauge()
                    .value();

            assertThat(lag).isEqualTo(-1.0);
        }
    }

    @Test
    void fetchLag_clampsToZero_whenLatestLessThanCommitted() throws Exception {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, "test-group", 1);
        MeterRegistry registry = new SimpleMeterRegistry();

        TopicPartition topicPartition = new TopicPartition(KafkaTopics.EVENT_CREATED, 0);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            // Latest offset = 80 (less than committed)
            ListOffsetsResult listOffsetsResult = mock(ListOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> latestFuture = mock(KafkaFuture.class);
            when(adminClient.listOffsets(any())).thenReturn(listOffsetsResult);
            when(listOffsetsResult.all()).thenReturn(latestFuture);
            ListOffsetsResult.ListOffsetsResultInfo info = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
            when(info.offset()).thenReturn(80L);
            when(latestFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(Map.of(topicPartition, info));

            // Committed offset = 100 (greater than latest)
            ListConsumerGroupOffsetsResult offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> offsetsFuture = mock(KafkaFuture.class);
            when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(offsetsResult);
            when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(offsetsFuture);
            Map<TopicPartition, OffsetAndMetadata> committedMap = Map.of(
                    topicPartition, new OffsetAndMetadata(100));
            when(offsetsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(committedMap);

            metrics.bindTo(registry);

            double lag = registry.get("kafka.consumer.lag")
                    .tag("topic", KafkaTopics.EVENT_CREATED)
                    .tag("partition", "0")
                    .tag("group", "test-group")
                    .gauge()
                    .value();

            // Math.max(0, 80 - 100) = 0
            assertThat(lag).isEqualTo(0.0);
        }
    }
}

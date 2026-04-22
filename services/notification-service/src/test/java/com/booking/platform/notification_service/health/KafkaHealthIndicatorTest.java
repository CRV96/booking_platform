package com.booking.platform.notification_service.health;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.notification_service.constants.NotificationConst;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    private static final Set<String> ALL_REQUIRED_TOPICS = Set.of(
            KafkaTopics.EVENT_CREATED,
            KafkaTopics.EVENT_UPDATED,
            KafkaTopics.EVENT_PUBLISHED,
            KafkaTopics.EVENT_CANCELLED,
            KafkaTopics.BOOKING_CREATED,
            KafkaTopics.BOOKING_CONFIRMED,
            KafkaTopics.BOOKING_CANCELLED,
            KafkaTopics.PAYMENT_FAILED
    );

    @Test
    void health_allTopicsPresent_returnsUp() throws Exception {
        Set<String> topics = new HashSet<>(ALL_REQUIRED_TOPICS);
        topics.add("extra.topic.one");
        topics.add("extra.topic.two");

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

            ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Set<String>> topicsFuture = mock(KafkaFuture.class);
            when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(topicsFuture);
            when(topicsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(topics);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get(NotificationConst.HealthDetails.BROKER_STATUS))
                    .isEqualTo(NotificationConst.HealthDetails.REACHABLE);
            assertThat(health.getDetails().get(NotificationConst.HealthDetails.REQUIRED_TOPICS_PRESENT))
                    .isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<String> missingTopics = (List<String>) health.getDetails()
                    .get(NotificationConst.HealthDetails.MISSING_TOPICS);
            assertThat(missingTopics).isEmpty();
            assertThat((int) health.getDetails().get(NotificationConst.HealthDetails.TOPIC_COUNT))
                    .isPositive();
        }
    }

    @Test
    void health_someTopicsMissing_returnsDown() throws Exception {
        Set<String> topics = Set.of(); // empty — all required topics missing

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

            ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Set<String>> topicsFuture = mock(KafkaFuture.class);
            when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(topicsFuture);
            when(topicsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(topics);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get(NotificationConst.HealthDetails.REQUIRED_TOPICS_PRESENT))
                    .isEqualTo(false);
            @SuppressWarnings("unchecked")
            List<String> missingTopics = (List<String>) health.getDetails()
                    .get(NotificationConst.HealthDetails.MISSING_TOPICS);
            assertThat(missingTopics).isNotEmpty();
        }
    }

    @Test
    void health_specificMissingTopicListed() throws Exception {
        // All required topics except PAYMENT_FAILED
        Set<String> topics = new HashSet<>(ALL_REQUIRED_TOPICS);
        topics.remove(KafkaTopics.PAYMENT_FAILED);

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

            ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Set<String>> topicsFuture = mock(KafkaFuture.class);
            when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(topicsFuture);
            when(topicsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(topics);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            Health health = indicator.health();

            @SuppressWarnings("unchecked")
            List<String> missingTopics = (List<String>) health.getDetails()
                    .get(NotificationConst.HealthDetails.MISSING_TOPICS);
            assertThat(missingTopics).containsExactly(KafkaTopics.PAYMENT_FAILED);
        }
    }

    @Test
    void health_brokerUnreachable_returnsDown() throws Exception {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

            ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Set<String>> topicsFuture = mock(KafkaFuture.class);
            when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(topicsFuture);
            when(topicsFuture.get(anyLong(), any(TimeUnit.class)))
                    .thenThrow(new RuntimeException("connection refused"));
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get(NotificationConst.HealthDetails.BROKER_STATUS))
                    .isEqualTo(NotificationConst.HealthDetails.UNREACHABLE);
            assertThat(health.getDetails().get(NotificationConst.HealthDetails.ERROR)).isNotNull();
        }
    }

    @Test
    void health_brokerUp_detailsIncludeTopicCount() throws Exception {
        Set<String> topics = new HashSet<>(ALL_REQUIRED_TOPICS);
        // Add extra topics to reach 15 total
        for (int i = 0; i < 7; i++) {
            topics.add("extra.topic." + i);
        }
        assertThat(topics).hasSize(15);

        KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);

        try (var adminClientMock = mockStatic(AdminClient.class)) {
            AdminClient adminClient = mock(AdminClient.class);
            adminClientMock.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

            ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
            @SuppressWarnings("unchecked")
            KafkaFuture<Set<String>> topicsFuture = mock(KafkaFuture.class);
            when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
            when(listTopicsResult.names()).thenReturn(topicsFuture);
            when(topicsFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(topics);
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

            Health health = indicator.health();

            assertThat(health.getDetails().get(NotificationConst.HealthDetails.TOPIC_COUNT))
                    .isEqualTo(15);
        }
    }
}

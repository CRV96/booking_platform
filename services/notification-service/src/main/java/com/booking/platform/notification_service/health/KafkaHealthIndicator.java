package com.booking.platform.notification_service.health;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.notification_service.constants.NotificationConst;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Custom Actuator health indicator for Kafka.
 *
 * <p>Exposed at {@code /actuator/health} under the key {@code "kafka"}.
 * Spring Boot's auto-configured Kafka health check only reports UP/DOWN based
 * on broker connectivity. This indicator goes further — it also verifies that
 * the topics notification-service depends on actually exist on the broker.
 *
 * <h2>What it checks</h2>
 * <ol>
 *   <li>Broker reachable: creates an {@link AdminClient} and calls
 *       {@link AdminClient#listTopics()} within a 3-second timeout.</li>
 *   <li>Required topics present: verifies every topic this service subscribes
 *       to is listed on the broker.</li>
 * </ol>
 *
 * <h2>Response shape</h2>
 * <pre>
 * {
 *   "status": "UP",
 *   "components": {
 *     "kafka": {
 *       "status": "UP",
 *       "details": {
 *         "brokerStatus": "reachable",
 *         "topicCount": 14,
 *         "requiredTopicsPresent": true,
 *         "missingTopics": []
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>If the broker is down or a required topic is missing, {@code "status"} is
 * {@code "DOWN"} and the detail shows the root cause.
 */
@Slf4j
@Component("kafka")
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 3;

    /**
     * Topics this service must consume from. If any of these are missing
     * from the broker, the health check reports DOWN.
     */
    private static final List<String> REQUIRED_TOPICS = List.of(
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

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            Set<String> topics = adminClient
                    .listTopics(new ListTopicsOptions().timeoutMs((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)))
                    .names()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<String> missingTopics = REQUIRED_TOPICS.stream()
                    .filter(t -> !topics.contains(t))
                    .toList();

            if (!missingTopics.isEmpty()) {
                return Health.down()
                        .withDetail(NotificationConst.HealthDetails.BROKER_STATUS, NotificationConst.HealthDetails.REACHABLE)
                        .withDetail(NotificationConst.HealthDetails.TOPIC_COUNT, topics.size())
                        .withDetail(NotificationConst.HealthDetails.REQUIRED_TOPICS_PRESENT, false)
                        .withDetail(NotificationConst.HealthDetails.MISSING_TOPICS, missingTopics)
                        .build();
            }

            return Health.up()
                    .withDetail(NotificationConst.HealthDetails.BROKER_STATUS, NotificationConst.HealthDetails.REACHABLE)
                    .withDetail(NotificationConst.HealthDetails.TOPIC_COUNT, topics.size())
                    .withDetail(NotificationConst.HealthDetails.REQUIRED_TOPICS_PRESENT, true)
                    .withDetail(NotificationConst.HealthDetails.MISSING_TOPICS, List.of())
                    .build();

        } catch (Exception e) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.NOTIFICATION_CONSUMER_ERROR,
                    "[KAFKA_HEALTH] Broker unreachable: {}", e.getMessage());
            return Health.down()
                    .withDetail(NotificationConst.HealthDetails.BROKER_STATUS, NotificationConst.HealthDetails.UNREACHABLE)
                    .withDetail(NotificationConst.HealthDetails.ERROR, e.getMessage())
                    .build();
        }
    }
}

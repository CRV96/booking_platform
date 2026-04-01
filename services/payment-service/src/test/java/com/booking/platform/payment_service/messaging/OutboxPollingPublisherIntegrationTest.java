package com.booking.platform.payment_service.messaging;

import com.booking.platform.common.events.KafkaTopics;
import com.booking.platform.payment_service.base.BaseIntegrationTest;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.messaging.publisher.OutboxPollingPublisher;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.booking.platform.payment_service.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link OutboxPollingPublisher}.
 *
 * <p>Verifies that outbox events written by PaymentServiceImpl are:
 * <ol>
 *   <li>Picked up by the poller</li>
 *   <li>Published to the correct Kafka topic</li>
 *   <li>Marked as published in the database</li>
 * </ol>
 *
 * <p>Uses a raw Kafka consumer (byte[] deserializer) to read from the topic,
 * since we only need to verify that a message was published, not its exact proto content.
 */
class OutboxPollingPublisherIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxPollingPublisher outboxPollingPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private PaymentGateway paymentGateway;

    private KafkaConsumer<String, byte[]> kafkaConsumer;

    @BeforeEach
    void setupGateway() {
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_outbox_test", "requires_confirmation", "card")));

        when(paymentGateway.confirmPayment(anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_outbox_test", "succeeded", "card")));

        when(paymentGateway.createRefund(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayRefundResponse("re_outbox_test", "succeeded")));
    }

    @BeforeEach
    void setupKafkaConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(List.of(
                KafkaTopics.PAYMENT_COMPLETED,
                KafkaTopics.PAYMENT_FAILED,
                KafkaTopics.PAYMENT_REFUND_COMPLETED));
    }

    @AfterEach
    void closeConsumer() {
        if (kafkaConsumer != null) {
            kafkaConsumer.close();
        }
    }

    @Test
    void pollAndPublish_completedPayment_publishesToKafkaAndMarksPublished() {
        String bookingId = "booking-outbox-completed-" + UUID.randomUUID();
        PaymentEntity payment = paymentService.processPayment(bookingId, "user-1", new BigDecimal("50.00"), "USD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Verify outbox row exists and is unpublished
        Long unpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        assertThat(unpublished).isGreaterThanOrEqualTo(1);

        // Trigger the poller manually (instead of waiting for @Scheduled)
        outboxPollingPublisher.pollAndPublish();

        // Verify outbox row is now marked as published
        Long stillUnpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                Long.class, payment.getId().toString());
        assertThat(stillUnpublished).isEqualTo(0);

        // Verify message arrived on the Kafka topic
        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        boolean foundOnCorrectTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_COMPLETED)
                    && bookingId.equals(record.key())) {
                foundOnCorrectTopic = true;
                assertThat(record.value()).isNotNull();
                assertThat(record.value().length).isGreaterThan(0);
            }
        }
        assertThat(foundOnCorrectTopic)
                .as("PaymentCompleted event should be on topic " + KafkaTopics.PAYMENT_COMPLETED)
                .isTrue();
    }

    @Test
    void pollAndPublish_failedPayment_publishesToFailedTopic() {
        when(paymentGateway.confirmPayment(anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_fail_test", "failed", "card")));

        String bookingId = "booking-outbox-failed-" + UUID.randomUUID();
        PaymentEntity payment = paymentService.processPayment(bookingId, "user-1", new BigDecimal("50.00"), "USD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundOnFailedTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_FAILED)
                    && bookingId.equals(record.key())) {
                foundOnFailedTopic = true;
            }
        }
        assertThat(foundOnFailedTopic)
                .as("PaymentFailed event should be on topic " + KafkaTopics.PAYMENT_FAILED)
                .isTrue();
    }

    @Test
    void pollAndPublish_refundCompleted_publishesToRefundTopic() {
        // First create a completed payment
        String bookingId = "booking-outbox-refund-" + UUID.randomUUID();
        PaymentEntity payment = paymentService.processPayment(bookingId, "user-1", new BigDecimal("75.00"), "USD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Publish the completed event first so it doesn't interfere
        outboxPollingPublisher.pollAndPublish();

        // Now process the refund
        paymentService.processRefund(bookingId);

        // Trigger the poller for the refund event
        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundOnRefundTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_REFUND_COMPLETED)
                    && bookingId.equals(record.key())) {
                foundOnRefundTopic = true;
            }
        }
        assertThat(foundOnRefundTopic)
                .as("RefundCompleted event should be on topic " + KafkaTopics.PAYMENT_REFUND_COMPLETED)
                .isTrue();
    }

    @Test
    void pollAndPublish_usesBookingIdAsKafkaKey() {
        String bookingId = "booking-key-test-" + UUID.randomUUID();
        paymentService.processPayment(bookingId, "user-1", new BigDecimal("10.00"), "USD");

        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundWithCorrectKey = false;
        for (var record : records) {
            if (bookingId.equals(record.key())) {
                foundWithCorrectKey = true;
            }
        }
        assertThat(foundWithCorrectKey)
                .as("Kafka message key should be the bookingId for partition affinity")
                .isTrue();
    }
}

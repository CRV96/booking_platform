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
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.payment_service.service.PaymentOutcomeService;
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
 * <p>Verifies that outbox events written by the payment flow (create order intent + apply the
 * outcome) are picked up by the poller, published to the correct Kafka topic, and marked published.
 *
 * <p>Uses a raw Kafka consumer (byte[] deserializer) — we only verify a message was published on
 * the right topic with the right key, not its exact proto content.
 */
class OutboxPollingPublisherIntegrationTest extends BaseIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentOutcomeService paymentOutcomeService;
    @Autowired private OutboxPollingPublisher outboxPollingPublisher;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private PaymentRepository paymentRepository;

    @MockBean private PaymentGateway paymentGateway;

    private KafkaConsumer<String, byte[]> kafkaConsumer;

    @BeforeEach
    void setupGateway() {
        // Return a PaymentIntent id derived from the idempotency key (3rd arg = orderId) so each
        // payment gets a unique external id — the outcome lookup (findByExternalPaymentId) needs it.
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_" + inv.getArgument(2), "requires_payment_method",
                                "card", "secret_" + inv.getArgument(2))));

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

    /** Creates a PROCESSING order payment and returns its record (external id = "pi_" + orderId). */
    private PaymentEntity createOrderPayment(String orderId) {
        paymentService.getOrCreateOrderPaymentIntent(
                orderId, "user-1", List.of("booking-" + orderId), new BigDecimal("50.00"), "USD");
        return paymentRepository.findByIdempotencyKey(orderId).orElseThrow();
    }

    @Test
    void pollAndPublish_completedPayment_publishesToKafkaAndMarksPublished() {
        String orderId = "order-outbox-completed-" + UUID.randomUUID();
        PaymentEntity payment = createOrderPayment(orderId);
        paymentOutcomeService.markSucceeded("pi_" + orderId);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);

        Long unpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        assertThat(unpublished).isGreaterThanOrEqualTo(1);

        outboxPollingPublisher.pollAndPublish();

        Long stillUnpublished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL",
                Long.class, payment.getId().toString());
        assertThat(stillUnpublished).isEqualTo(0);

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        boolean foundOnCorrectTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_COMPLETED) && orderId.equals(record.key())) {
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
        String orderId = "order-outbox-failed-" + UUID.randomUUID();
        createOrderPayment(orderId);
        paymentOutcomeService.markFailed("pi_" + orderId, "card_declined");

        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundOnFailedTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_FAILED) && orderId.equals(record.key())) {
                foundOnFailedTopic = true;
            }
        }
        assertThat(foundOnFailedTopic)
                .as("PaymentFailed event should be on topic " + KafkaTopics.PAYMENT_FAILED)
                .isTrue();
    }

    @Test
    void pollAndPublish_refundCompleted_publishesToRefundTopic() {
        String orderId = "order-outbox-refund-" + UUID.randomUUID();
        createOrderPayment(orderId);
        paymentOutcomeService.markSucceeded("pi_" + orderId);

        // Publish the completed event first so it doesn't interfere
        outboxPollingPublisher.pollAndPublish();

        // processRefund looks the payment up by bookingId — for an order that's the orderId
        paymentService.processRefund(orderId);
        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundOnRefundTopic = false;
        for (var record : records) {
            if (record.topic().equals(KafkaTopics.PAYMENT_REFUND_COMPLETED) && orderId.equals(record.key())) {
                foundOnRefundTopic = true;
            }
        }
        assertThat(foundOnRefundTopic)
                .as("RefundCompleted event should be on topic " + KafkaTopics.PAYMENT_REFUND_COMPLETED)
                .isTrue();
    }

    @Test
    void pollAndPublish_usesBookingIdAsKafkaKey() {
        String orderId = "order-key-test-" + UUID.randomUUID();
        createOrderPayment(orderId);
        paymentOutcomeService.markSucceeded("pi_" + orderId);

        outboxPollingPublisher.pollAndPublish();

        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(10));

        boolean foundWithCorrectKey = false;
        for (var record : records) {
            if (orderId.equals(record.key())) {
                foundWithCorrectKey = true;
            }
        }
        assertThat(foundWithCorrectKey)
                .as("Kafka message key should be the payment's aggregate id for partition affinity")
                .isTrue();
    }
}

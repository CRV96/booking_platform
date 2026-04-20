package com.booking.platform.payment_service.messaging.publisher;

import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxPollingPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, MessageLite> kafkaTemplate;

    @InjectMocks private OutboxPollingPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(publisher, "batchSize", 100);
        ReflectionTestUtils.setField(publisher, "retentionHours", 24);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, MessageLite>> successFuture() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private OutboxEventEntity outboxEvent(String eventType, String payload) {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("Payment")
                .aggregateId(UUID.randomUUID().toString())
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    private String completedPayload(UUID paymentId) {
        return String.format(
                "{\"payment_id\":\"%s\",\"booking_id\":\"booking-1\",\"amount\":\"99.99\",\"currency\":\"USD\",\"timestamp\":\"2024-01-01T00:00:00Z\"}",
                paymentId);
    }

    private String failedPayload(UUID paymentId) {
        return String.format(
                "{\"payment_id\":\"%s\",\"booking_id\":\"booking-1\",\"reason\":\"Card declined\",\"timestamp\":\"2024-01-01T00:00:00Z\"}",
                paymentId);
    }

    private String refundPayload(UUID paymentId) {
        return String.format(
                "{\"payment_id\":\"%s\",\"booking_id\":\"booking-1\",\"refund_id\":\"re_123\",\"amount\":\"99.99\",\"currency\":\"USD\",\"timestamp\":\"2024-01-01T00:00:00Z\"}",
                paymentId);
    }

    // ── pollAndPublish ────────────────────────────────────────────────────────

    @Test
    void pollAndPublish_noEvents_doesNotPublish() {
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of());

        publisher.pollAndPublish();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void pollAndPublish_paymentCompletedEvent_publishesToCorrectTopic() throws Exception {
        UUID paymentId = UUID.randomUUID();
        OutboxEventEntity event = outboxEvent("PaymentCompleted", completedPayload(paymentId));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        publisher.pollAndPublish();

        verify(kafkaTemplate).send(eq("events.payment.completed"), anyString(), any());
    }

    @Test
    void pollAndPublish_paymentFailedEvent_publishesToCorrectTopic() throws Exception {
        UUID paymentId = UUID.randomUUID();
        OutboxEventEntity event = outboxEvent("PaymentFailed", failedPayload(paymentId));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        publisher.pollAndPublish();

        verify(kafkaTemplate).send(eq("events.payment.failed"), anyString(), any());
    }

    @Test
    void pollAndPublish_refundCompletedEvent_publishesToCorrectTopic() throws Exception {
        UUID paymentId = UUID.randomUUID();
        OutboxEventEntity event = outboxEvent("RefundCompleted", refundPayload(paymentId));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        publisher.pollAndPublish();

        verify(kafkaTemplate).send(eq("events.payment.refund-completed"), anyString(), any());
    }

    @Test
    void pollAndPublish_onSuccess_setsPublishedAt() {
        UUID paymentId = UUID.randomUUID();
        OutboxEventEntity event = outboxEvent("PaymentCompleted", completedPayload(paymentId));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publisher.pollAndPublish();

        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    void pollAndPublish_kafkaFails_stopsProcessingRemainingEvents() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEventEntity event1 = outboxEvent("PaymentCompleted", completedPayload(id1));
        OutboxEventEntity event2 = outboxEvent("PaymentFailed", failedPayload(id2));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

        publisher.pollAndPublish();

        // First event fails → processing stops → second event not published
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        assertThat(event1.getPublishedAt()).isNull();
        assertThat(event2.getPublishedAt()).isNull();
    }

    @Test
    void pollAndPublish_multipleEvents_publishesAllInOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEventEntity event1 = outboxEvent("PaymentCompleted", completedPayload(id1));
        OutboxEventEntity event2 = outboxEvent("PaymentFailed", failedPayload(id2));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publisher.pollAndPublish();

        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
        assertThat(event1.getPublishedAt()).isNotNull();
        assertThat(event2.getPublishedAt()).isNotNull();
    }

    @Test
    void pollAndPublish_usesBookingIdAsKafkaKey() {
        UUID paymentId = UUID.randomUUID();
        OutboxEventEntity event = outboxEvent("PaymentCompleted", completedPayload(paymentId));
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        publisher.pollAndPublish();

        verify(kafkaTemplate).send(anyString(), eq("booking-1"), any());
    }

    @Test
    void pollAndPublish_payloadMissingBookingId_stopsProcessing() {
        String invalidPayload = "{\"payment_id\":\"p1\",\"amount\":\"99.99\"}";
        OutboxEventEntity event = outboxEvent("PaymentCompleted", invalidPayload);
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));

        // Should not throw — caught internally and logged
        publisher.pollAndPublish();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // ── cleanup ───────────────────────────────────────────────────────────────

    @Test
    void cleanup_deletesEventsOlderThanRetentionHours() {
        publisher.cleanup();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxEventRepository).deleteByPublishedAtBefore(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedCutoff = Instant.now().minusSeconds(24 * 3600);
        // Allow 5-second tolerance for test execution time
        assertThat(cutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    }
}

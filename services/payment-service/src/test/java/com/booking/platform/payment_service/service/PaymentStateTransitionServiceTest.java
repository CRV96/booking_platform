package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentNotFoundException;
import com.booking.platform.payment_service.repository.OutboxEventRepository;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.impl.PaymentStateTransitionService;
import com.booking.platform.payment_service.validation.PaymentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentStateTransitionServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PaymentValidator paymentValidator;

    @InjectMocks private PaymentStateTransitionService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2025-06-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "maxRetries", 3);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private UUID id() { return UUID.randomUUID(); }

    private PaymentEntity payment(UUID id, PaymentStatus status) {
        return PaymentEntity.builder()
                .id(id)
                .bookingId("booking-1")
                .userId("user-1")
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(status)
                .retryCount(0)
                .maxRetries(3)
                .build();
    }

    // ── updateToProcessing ────────────────────────────────────────────────────

    @Test
    void updateToProcessing_setsExternalIdAndMethodAndStatus() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.INITIATED)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GatewayPaymentResponse response = new GatewayPaymentResponse("pi_123", "requires_confirmation", "card");
        PaymentEntity result = service.updateToProcessing(id, response);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(result.getExternalPaymentId()).isEqualTo("pi_123");
        assertThat(result.getPaymentMethod()).isEqualTo("card");
        verify(paymentValidator).assertValidTransition(any(), eq(PaymentStatus.PROCESSING));
    }

    // ── createOrderPaymentRecord ──────────────────────────────────────────────

    @Test
    void createOrderPaymentRecord_savesWithOrderIdAndBookingIds() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentEntity result = service.createOrderPaymentRecord(
                "order-1", "user-1", List.of("b1", "b2"), new BigDecimal("100.00"), "USD");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(result.getBookingId()).isEqualTo("order-1");
        assertThat(result.getBookingIds()).isEqualTo("b1,b2");
        assertThat(result.getIdempotencyKey()).isEqualTo("order-1");
    }

    @Test
    void markCompleted_orderPayment_outboxPayloadContainsAllBookingIds() throws Exception {
        UUID id = id();
        PaymentEntity orderPayment = PaymentEntity.builder()
                .id(id).bookingId("order-1").bookingIds("b1,b2").userId("u")
                .amount(BigDecimal.TEN).currency("USD").status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(orderPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markCompleted(id, new GatewayPaymentResponse("pi", "succeeded", "card"));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("booking_ids")).hasSize(2);
        assertThat(payload.get("booking_ids").get(0).asText()).isEqualTo("b1");
        assertThat(payload.get("booking_ids").get(1).asText()).isEqualTo("b2");
    }

    // ── markCompleted ─────────────────────────────────────────────────────────

    @Test
    void markCompleted_setsStatusAndSavesOutboxEvent() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GatewayPaymentResponse response = new GatewayPaymentResponse("pi_123", "succeeded", "card");
        PaymentEntity result = service.markCompleted(id, response);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("PaymentCompleted");
    }

    @Test
    void markCompleted_outboxPayloadContainsAmountAndCurrency() throws Exception {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markCompleted(id, new GatewayPaymentResponse("pi_123", "succeeded", "card"));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.has("amount")).isTrue();
        assertThat(payload.has("currency")).isTrue();
        assertThat(payload.get("currency").asText()).isEqualTo("USD");
    }

    // ── markFailed ────────────────────────────────────────────────────────────

    @Test
    void markFailed_setsStatusAndReasonAndSavesOutboxEvent() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentEntity result = service.markFailed(id, "Card declined");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Card declined");

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("PaymentFailed");
    }

    @Test
    void markFailed_outboxPayloadContainsReason() throws Exception {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(id, "Insufficient funds");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("reason").asText()).isEqualTo("Insufficient funds");
    }

    @Test
    void markFailed_nullReason_usesUnknown() throws Exception {
        UUID id = id();
        PaymentEntity p = PaymentEntity.builder()
                .id(id).bookingId("b").userId("u").amount(BigDecimal.TEN).currency("USD")
                .status(PaymentStatus.PROCESSING).retryCount(0).maxRetries(3)
                .build();
        p.setFailureReason(null);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(p));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(id, null);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("reason").asText()).isEqualTo("Unknown");
    }

    // ── markRefundInitiated ───────────────────────────────────────────────────

    @Test
    void markRefundInitiated_completedPayment_movesToRefundInitiated() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.COMPLETED)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentEntity result = service.markRefundInitiated(id);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUND_INITIATED);
        verify(paymentRepository).save(any());
    }

    @Test
    void markRefundInitiated_nonCompletedPayment_returnsWithoutSaving() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.FAILED)));

        PaymentEntity result = service.markRefundInitiated(id);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository, never()).save(any());
    }

    // ── markRefunded ──────────────────────────────────────────────────────────

    @Test
    void markRefunded_setsStatusAndSavesOutboxEvent() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.REFUND_INITIATED)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentEntity result = service.markRefunded(id, new GatewayRefundResponse("re_123", "succeeded"));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("RefundCompleted");
    }

    @Test
    void markRefunded_outboxPayloadContainsRefundId() throws Exception {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.REFUND_INITIATED)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markRefunded(id, new GatewayRefundResponse("re_456", "succeeded"));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("refund_id").asText()).isEqualTo("re_456");
    }

    // ── findOrThrow ───────────────────────────────────────────────────────────

    @Test
    void anyMethod_paymentNotFound_throwsPaymentNotFoundException() {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateToProcessing(id, new GatewayPaymentResponse("pi", "req", "card")))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── outbox common fields ──────────────────────────────────────────────────

    @Test
    void markCompleted_outboxPayloadContainsPaymentIdAndBookingId() throws Exception {
        UUID id = id();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markCompleted(id, new GatewayPaymentResponse("pi", "succeeded", "card"));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.has("payment_id")).isTrue();
        assertThat(payload.has("booking_id")).isTrue();
        assertThat(payload.has("timestamp")).isTrue();
        assertThat(payload.get("booking_id").asText()).isEqualTo("booking-1");
    }
}

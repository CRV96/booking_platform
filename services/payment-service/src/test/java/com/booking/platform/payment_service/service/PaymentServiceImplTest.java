package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.impl.PaymentServiceImpl;
import com.booking.platform.payment_service.service.impl.PaymentStateTransitionService;
import com.booking.platform.payment_service.validation.PaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentStateTransitionService transitions;
    @Mock private PaymentValidator paymentValidator;

    @InjectMocks private PaymentServiceImpl service;

    private static final String BOOKING_ID = "booking-1";
    private static final String USER_ID    = "user-1";
    private static final BigDecimal AMOUNT = new BigDecimal("99.99");
    private static final String CURRENCY   = "USD";

    private PaymentEntity payment(UUID id, PaymentStatus status) {
        return PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID)
                .amount(AMOUNT).currency(CURRENCY)
                .status(status).retryCount(0).maxRetries(3)
                .build();
    }

    // ── processPayment ────────────────────────────────────────────────────────

    @Test
    void processPayment_existingPayment_returnsIdempotently() {
        UUID id = UUID.randomUUID();
        PaymentEntity existing = payment(id, PaymentStatus.COMPLETED);
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.of(existing));

        PaymentEntity result = service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        assertThat(result).isSameAs(existing);
        verify(transitions, never()).createPaymentRecord(any(), any(), any(), any());
        verify(paymentGateway, never()).createPaymentIntent(any(), any(), any());
    }

    @Test
    void processPayment_success_callsGatewayAndMarksCompleted() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(BOOKING_ID, USER_ID, AMOUNT, CURRENCY))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(transitions.updateToProcessing(eq(id), any()))
                .thenReturn(payment(id, PaymentStatus.PROCESSING));

        GatewayPaymentResponse createResp = new GatewayPaymentResponse("pi_123", "requires_confirmation", "card");
        GatewayPaymentResponse confirmResp = new GatewayPaymentResponse("pi_123", "succeeded", "card");
        when(paymentGateway.createPaymentIntent(AMOUNT, CURRENCY, BOOKING_ID))
                .thenReturn(CompletableFuture.completedFuture(createResp));
        when(paymentGateway.confirmPayment("pi_123"))
                .thenReturn(CompletableFuture.completedFuture(confirmResp));

        PaymentEntity completed = payment(id, PaymentStatus.COMPLETED);
        when(transitions.markCompleted(eq(id), any())).thenReturn(completed);

        PaymentEntity result = service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(transitions).markCompleted(eq(id), any());
    }

    @Test
    void processPayment_normalizesLowercaseCurrency() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(eq(BOOKING_ID), eq(USER_ID), eq(AMOUNT), eq("USD")))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(transitions.updateToProcessing(any(), any())).thenReturn(payment(id, PaymentStatus.PROCESSING));
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new GatewayPaymentResponse("pi", "req", "card")));
        when(paymentGateway.confirmPayment(any()))
                .thenReturn(CompletableFuture.completedFuture(new GatewayPaymentResponse("pi", "succeeded", "card")));
        when(transitions.markCompleted(any(), any())).thenReturn(payment(id, PaymentStatus.COMPLETED));

        service.processPayment(BOOKING_ID, USER_ID, AMOUNT, "usd");

        verify(transitions).createPaymentRecord(BOOKING_ID, USER_ID, AMOUNT, "USD");
    }

    @Test
    void processPayment_gatewayBusinessError_marksPaymentFailed() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(any(), any(), any(), any()))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new PaymentGatewayException("Card declined", null)));
        PaymentEntity failed = payment(id, PaymentStatus.FAILED);
        when(transitions.markFailed(eq(id), any())).thenReturn(failed);

        PaymentEntity result = service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(transitions).markFailed(eq(id), eq("Card declined"));
    }

    @Test
    void processPayment_gatewayUnavailable_marksPaymentPendingRetry() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(any(), any(), any(), any()))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new PaymentGatewayUnavailableException("Circuit open")));
        PaymentEntity pending = payment(id, PaymentStatus.PENDING_RETRY);
        when(transitions.markPendingRetry(eq(id), any())).thenReturn(pending);

        PaymentEntity result = service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING_RETRY);
        verify(transitions).markPendingRetry(eq(id), eq("Circuit open"));
    }

    @Test
    void processPayment_confirmUnexpectedStatus_marksPaymentFailed() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(BOOKING_ID)).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(any(), any(), any(), any()))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(transitions.updateToProcessing(any(), any())).thenReturn(payment(id, PaymentStatus.PROCESSING));
        when(paymentGateway.createPaymentIntent(any(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new GatewayPaymentResponse("pi", "req", "card")));
        when(paymentGateway.confirmPayment(any()))
                .thenReturn(CompletableFuture.completedFuture(new GatewayPaymentResponse("pi", "failed", "card")));
        when(transitions.markFailed(any(), any())).thenReturn(payment(id, PaymentStatus.FAILED));

        PaymentEntity result = service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(transitions).markFailed(eq(id), contains("failed"));
    }

    @Test
    void processPayment_callsValidation() {
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transitions.createPaymentRecord(any(), any(), any(), any()))
                .thenReturn(payment(UUID.randomUUID(), PaymentStatus.INITIATED));
        when(paymentGateway.createPaymentIntent(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new PaymentGatewayException("err", null)));
        when(transitions.markFailed(any(), any())).thenReturn(payment(UUID.randomUUID(), PaymentStatus.FAILED));

        service.processPayment(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);

        verify(paymentValidator).validatePaymentForProcessing(BOOKING_ID, USER_ID, AMOUNT, CURRENCY);
    }

    // ── processRefund ─────────────────────────────────────────────────────────

    @Test
    void processRefund_noPaymentFound_returnsEarly() {
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        service.processRefund(BOOKING_ID);

        verify(transitions, never()).markRefundInitiated(any());
        verify(paymentGateway, never()).createRefund(any(), any());
    }

    @Test
    void processRefund_paymentNotCompleted_skipsRefund() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.FAILED)));

        service.processRefund(BOOKING_ID);

        verify(transitions, never()).markRefundInitiated(any());
        verify(paymentGateway, never()).createRefund(any(), any());
    }

    @Test
    void processRefund_alreadyConcurrentlyRefunding_guardFires() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.COMPLETED)));
        // Guard returns COMPLETED (another process changed it first)
        when(transitions.markRefundInitiated(id)).thenReturn(payment(id, PaymentStatus.COMPLETED));

        service.processRefund(BOOKING_ID);

        verify(paymentGateway, never()).createRefund(any(), any());
    }

    @Test
    void processRefund_success_callsGatewayAndMarksRefunded() {
        UUID id = UUID.randomUUID();
        PaymentEntity completedPayment = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID)
                .amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.COMPLETED).externalPaymentId("pi_123")
                .retryCount(0).maxRetries(3)
                .build();
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(completedPayment));
        PaymentEntity refundInitiated = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID)
                .amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.REFUND_INITIATED).externalPaymentId("pi_123")
                .retryCount(0).maxRetries(3)
                .build();
        when(transitions.markRefundInitiated(id)).thenReturn(refundInitiated);

        GatewayRefundResponse refundResp = new GatewayRefundResponse("re_456", "succeeded");
        when(paymentGateway.createRefund("pi_123", AMOUNT))
                .thenReturn(CompletableFuture.completedFuture(refundResp));
        when(transitions.markRefunded(eq(id), any())).thenReturn(payment(id, PaymentStatus.REFUNDED));

        service.processRefund(BOOKING_ID);

        verify(paymentGateway).createRefund("pi_123", AMOUNT);
        verify(transitions).markRefunded(eq(id), any());
    }

    @Test
    void processRefund_gatewayUnavailable_leavesAsRefundInitiated() {
        UUID id = UUID.randomUUID();
        PaymentEntity completedPayment = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID)
                .amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.COMPLETED).externalPaymentId("pi_123")
                .retryCount(0).maxRetries(3)
                .build();
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(completedPayment));
        when(transitions.markRefundInitiated(id)).thenReturn(payment(id, PaymentStatus.REFUND_INITIATED));
        when(paymentGateway.createRefund(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new PaymentGatewayUnavailableException("Stripe down")));

        service.processRefund(BOOKING_ID);

        verify(transitions, never()).markRefunded(any(), any());
    }

    @Test
    void processRefund_callsBookingIdValidation() {
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        service.processRefund(BOOKING_ID);

        verify(paymentValidator).validateBookingId(BOOKING_ID);
    }

    // ── retryPayment ──────────────────────────────────────────────────────────

    @Test
    void retryPayment_noExternalId_callsCreateAndConfirm() {
        UUID id = UUID.randomUUID();
        PaymentEntity processing = payment(id, PaymentStatus.PROCESSING);
        when(transitions.incrementRetryCount(id)).thenReturn(processing);

        GatewayPaymentResponse createResp = new GatewayPaymentResponse("pi_new", "req", "card");
        GatewayPaymentResponse confirmResp = new GatewayPaymentResponse("pi_new", "succeeded", "card");
        when(paymentGateway.createPaymentIntent(AMOUNT, CURRENCY, BOOKING_ID))
                .thenReturn(CompletableFuture.completedFuture(createResp));
        when(paymentGateway.confirmPayment("pi_new"))
                .thenReturn(CompletableFuture.completedFuture(confirmResp));
        when(transitions.markCompleted(eq(id), any())).thenReturn(payment(id, PaymentStatus.COMPLETED));
        when(transitions.updateToProcessing(any(), any())).thenReturn(processing);

        service.retryPayment(payment(id, PaymentStatus.PENDING_RETRY));

        verify(paymentGateway).createPaymentIntent(AMOUNT, CURRENCY, BOOKING_ID);
        verify(paymentGateway).confirmPayment("pi_new");
        verify(transitions).markCompleted(eq(id), any());
    }

    @Test
    void retryPayment_withExternalId_skipsCreateAndOnlyConfirms() {
        UUID id = UUID.randomUUID();
        PaymentEntity processing = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID)
                .amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.PROCESSING).externalPaymentId("pi_existing")
                .retryCount(1).maxRetries(3)
                .build();
        when(transitions.incrementRetryCount(id)).thenReturn(processing);

        GatewayPaymentResponse confirmResp = new GatewayPaymentResponse("pi_existing", "succeeded", "card");
        when(paymentGateway.confirmPayment("pi_existing"))
                .thenReturn(CompletableFuture.completedFuture(confirmResp));
        when(transitions.markCompleted(eq(id), any())).thenReturn(payment(id, PaymentStatus.COMPLETED));

        service.retryPayment(payment(id, PaymentStatus.PENDING_RETRY));

        verify(paymentGateway, never()).createPaymentIntent(any(), any(), any());
        verify(paymentGateway).confirmPayment("pi_existing");
    }

    @Test
    void retryPayment_gatewayStillDown_belowMaxRetries_remainsPendingRetry() {
        UUID id = UUID.randomUUID();
        PaymentEntity processing = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID).amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.PROCESSING).retryCount(1).maxRetries(3).build();
        when(transitions.incrementRetryCount(id)).thenReturn(processing);
        when(paymentGateway.createPaymentIntent(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new PaymentGatewayUnavailableException("Still down")));
        when(transitions.markPendingRetry(any(), any())).thenReturn(payment(id, PaymentStatus.PENDING_RETRY));

        service.retryPayment(payment(id, PaymentStatus.PENDING_RETRY));

        verify(transitions).markPendingRetry(eq(id), any());
        verify(transitions, never()).markFailed(any(), any());
    }

    @Test
    void retryPayment_gatewayDown_maxRetriesExhausted_marksAsFailed() {
        UUID id = UUID.randomUUID();
        PaymentEntity processing = PaymentEntity.builder()
                .id(id).bookingId(BOOKING_ID).userId(USER_ID).amount(AMOUNT).currency(CURRENCY)
                .status(PaymentStatus.PROCESSING).retryCount(3).maxRetries(3).build();
        when(transitions.incrementRetryCount(id)).thenReturn(processing);
        when(paymentGateway.createPaymentIntent(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new PaymentGatewayUnavailableException("Gateway down")));
        when(transitions.markFailed(any(), any())).thenReturn(payment(id, PaymentStatus.FAILED));

        service.retryPayment(payment(id, PaymentStatus.PENDING_RETRY));

        verify(transitions).markFailed(eq(id), contains("Max retries exhausted"));
        verify(transitions, never()).markPendingRetry(any(), any());
    }
}

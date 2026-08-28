package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.dto.PaymentIntentResult;
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
import java.util.List;
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

    private PaymentEntity paymentWithExternalId(UUID id, PaymentStatus status, String externalId) {
        PaymentEntity payment = payment(id, status);
        payment.setExternalPaymentId(externalId);
        return payment;
    }

    // ── getOrCreateOrderPaymentIntent (checkout: create-only, one payment for N bookings) ──

    private static final String ORDER_ID = "order-1";
    private static final List<String> BOOKING_IDS = List.of("booking-1", "booking-2");

    @Test
    void getOrCreateOrderPaymentIntent_new_createsIntentAndReturnsClientSecret() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(ORDER_ID)).thenReturn(Optional.empty());
        when(transitions.createOrderPaymentRecord(ORDER_ID, USER_ID, BOOKING_IDS, AMOUNT, CURRENCY))
                .thenReturn(payment(id, PaymentStatus.INITIATED));
        when(paymentGateway.createPaymentIntent(AMOUNT, CURRENCY, ORDER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_o", "requires_payment_method", "card", "pi_o_secret")));
        when(transitions.updateToProcessing(eq(id), any()))
                .thenReturn(paymentWithExternalId(id, PaymentStatus.PROCESSING, "pi_o"));

        PaymentIntentResult result = service.getOrCreateOrderPaymentIntent(ORDER_ID, USER_ID, BOOKING_IDS, AMOUNT, CURRENCY);

        assertThat(result.clientSecret()).isEqualTo("pi_o_secret");
        assertThat(result.status()).isEqualTo("PROCESSING");
        verify(paymentGateway, never()).retrievePaymentIntent(any());
    }

    @Test
    void getOrCreateOrderPaymentIntent_existingAwaiting_retrievesFreshSecret() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(ORDER_ID))
                .thenReturn(Optional.of(paymentWithExternalId(id, PaymentStatus.PROCESSING, "pi_o")));
        when(paymentGateway.retrievePaymentIntent("pi_o"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_o", "requires_payment_method", "card", "fresh_secret")));

        PaymentIntentResult result = service.getOrCreateOrderPaymentIntent(ORDER_ID, USER_ID, BOOKING_IDS, AMOUNT, CURRENCY);

        assertThat(result.clientSecret()).isEqualTo("fresh_secret");
        verify(paymentGateway, never()).createPaymentIntent(any(), anyString(), anyString());
    }

    @Test
    void getOrCreateOrderPaymentIntent_alreadyResolved_returnsStatusWithoutGatewayCall() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(ORDER_ID))
                .thenReturn(Optional.of(paymentWithExternalId(id, PaymentStatus.COMPLETED, "pi_o")));

        PaymentIntentResult result = service.getOrCreateOrderPaymentIntent(ORDER_ID, USER_ID, BOOKING_IDS, AMOUNT, CURRENCY);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.clientSecret()).isNull();
        verify(paymentGateway, never()).retrievePaymentIntent(any());
        verify(paymentGateway, never()).createPaymentIntent(any(), anyString(), anyString());
    }

    @Test
    void getOrCreateOrderPaymentIntent_interruptedCreate_finishesCreate() {
        UUID id = UUID.randomUUID();
        // Record exists but has no PaymentIntent yet (creation was interrupted). resolveExistingIntent
        // re-creates the intent using the payment's own bookingId as the idempotency key.
        when(paymentRepository.findByIdempotencyKey(ORDER_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.INITIATED)));
        when(paymentGateway.createPaymentIntent(eq(AMOUNT), eq(CURRENCY), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new GatewayPaymentResponse("pi_o", "requires_payment_method", "card", "pi_o_secret")));
        when(transitions.updateToProcessing(eq(id), any()))
                .thenReturn(paymentWithExternalId(id, PaymentStatus.PROCESSING, "pi_o"));

        PaymentIntentResult result = service.getOrCreateOrderPaymentIntent(ORDER_ID, USER_ID, BOOKING_IDS, AMOUNT, CURRENCY);

        assertThat(result.clientSecret()).isEqualTo("pi_o_secret");
        verify(paymentGateway).createPaymentIntent(eq(AMOUNT), eq(CURRENCY), anyString());
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

}

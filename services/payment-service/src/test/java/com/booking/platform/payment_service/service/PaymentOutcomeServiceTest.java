package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.impl.PaymentOutcomeServiceImpl;
import com.booking.platform.payment_service.service.impl.PaymentStateTransitionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOutcomeServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentStateTransitionService transitions;

    @InjectMocks private PaymentOutcomeServiceImpl service;

    private static final String EXTERNAL_ID = "pi_123";

    private PaymentEntity payment(UUID id, PaymentStatus status) {
        return PaymentEntity.builder()
                .id(id).bookingId("booking-1").userId("user-1")
                .amount(new BigDecimal("99.99")).currency("USD")
                .status(status).externalPaymentId(EXTERNAL_ID)
                .retryCount(0).maxRetries(3)
                .build();
    }

    @Test
    void markSucceeded_processing_marksCompleted() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));

        service.markSucceeded(EXTERNAL_ID);

        verify(transitions).markCompleted(eq(id), any());
    }

    @Test
    void markSucceeded_alreadyCompleted_isIdempotentNoOp() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.COMPLETED)));

        service.markSucceeded(EXTERNAL_ID);

        verify(transitions, never()).markCompleted(any(), any());
    }

    @Test
    void markSucceeded_unknownPaymentIntent_isIgnored() {
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID)).thenReturn(Optional.empty());

        service.markSucceeded(EXTERNAL_ID);

        verify(transitions, never()).markCompleted(any(), any());
    }

    @Test
    void markSucceeded_concurrentConflict_butResolvedOnReRead_isSwallowed() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(transitions.markCompleted(eq(id), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("version conflict", null));
        // Another handler already completed it
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.COMPLETED)));

        // Should NOT throw — the conflict just means "already done"
        service.markSucceeded(EXTERNAL_ID);

        verify(paymentRepository).findById(id);
    }

    @Test
    void markSucceeded_concurrentConflict_stillUnresolvedOnReRead_rethrows() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));
        when(transitions.markCompleted(eq(id), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("version conflict", null));
        // Re-read still shows an unresolved state — genuinely unexpected
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));

        assertThatThrownBy(() -> service.markSucceeded(EXTERNAL_ID))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void markFailed_processing_marksFailed() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.PROCESSING)));

        service.markFailed(EXTERNAL_ID, "card_declined");

        verify(transitions).markFailed(id, "card_declined");
    }

    @Test
    void markFailed_alreadyCompleted_doesNotOverwrite() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findByExternalPaymentId(EXTERNAL_ID))
                .thenReturn(Optional.of(payment(id, PaymentStatus.COMPLETED)));

        service.markFailed(EXTERNAL_ID, "card_declined");

        verify(transitions, never()).markFailed(any(), any());
    }
}

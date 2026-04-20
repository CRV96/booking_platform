package com.booking.platform.payment_service.scheduler;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRetrySchedulerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks private PaymentRetryScheduler scheduler;

    private PaymentEntity duePayment() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .bookingId("booking-" + UUID.randomUUID())
                .userId("user-1")
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.PENDING_RETRY)
                .nextRetryAt(Instant.now().minusSeconds(10))
                .retryCount(1)
                .maxRetries(3)
                .build();
    }

    // ── retryDuePayments ──────────────────────────────────────────────────────

    @Test
    void retryDuePayments_noDuePayments_doesNotCallService() {
        when(paymentRepository.findByStatusAndNextRetryAtBefore(
                eq(PaymentStatus.PENDING_RETRY), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.retryDuePayments();

        verify(paymentService, never()).retryPayment(any());
    }

    @Test
    void retryDuePayments_oneDuePayment_callsRetryPayment() {
        PaymentEntity due = duePayment();
        when(paymentRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of(due));

        scheduler.retryDuePayments();

        verify(paymentService).retryPayment(due);
    }

    @Test
    void retryDuePayments_multipleDuePayments_retriesAll() {
        PaymentEntity p1 = duePayment();
        PaymentEntity p2 = duePayment();
        PaymentEntity p3 = duePayment();
        when(paymentRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of(p1, p2, p3));

        scheduler.retryDuePayments();

        verify(paymentService).retryPayment(p1);
        verify(paymentService).retryPayment(p2);
        verify(paymentService).retryPayment(p3);
    }

    @Test
    void retryDuePayments_optimisticLockException_skipsAndContinues() {
        PaymentEntity p1 = duePayment();
        PaymentEntity p2 = duePayment();
        when(paymentRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of(p1, p2));
        doThrow(new OptimisticLockException("already claimed"))
                .when(paymentService).retryPayment(p1);

        scheduler.retryDuePayments();

        // p2 must still be retried even though p1 threw
        verify(paymentService).retryPayment(p1);
        verify(paymentService).retryPayment(p2);
    }

    @Test
    void retryDuePayments_runtimeException_skipsAndContinues() {
        PaymentEntity p1 = duePayment();
        PaymentEntity p2 = duePayment();
        when(paymentRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of(p1, p2));
        doThrow(new RuntimeException("unexpected error"))
                .when(paymentService).retryPayment(p1);

        scheduler.retryDuePayments();

        verify(paymentService).retryPayment(p1);
        verify(paymentService).retryPayment(p2);
    }

    @Test
    void retryDuePayments_queriesWithCurrentTime() {
        when(paymentRepository.findByStatusAndNextRetryAtBefore(any(), any()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        scheduler.retryDuePayments();
        Instant after = Instant.now();

        verify(paymentRepository).findByStatusAndNextRetryAtBefore(
                eq(PaymentStatus.PENDING_RETRY),
                argThat(cutoff -> !cutoff.isBefore(before) && !cutoff.isAfter(after)));
    }
}

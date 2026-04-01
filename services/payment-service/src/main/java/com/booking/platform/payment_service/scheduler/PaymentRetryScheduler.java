package com.booking.platform.payment_service.scheduler;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled task that retries payments stuck in {@link PaymentStatus#PENDING_RETRY}.
 *
 * <p>Payments enter {@code PENDING_RETRY} when Resilience4j exhausts all gateway
 * attempts (circuit open, timeout, bulkhead full). This scheduler polls for payments
 * whose backoff window has elapsed and attempts to process them again.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Runs every {@code payment.retry.scheduler.interval} ms (default: 60 000 = 1 min).</li>
 *   <li>Picks up all due payments in one tick — does not stop on individual failures,
 *       so one stuck payment cannot block others (contrast with {@code OutboxPollingPublisher}
 *       which stops on failure to preserve ordering).</li>
 *   <li>Exponential backoff is managed by {@code PaymentStateTransitionService#markPendingRetry};
 *       this scheduler just asks "which ones are due?" via {@code nextRetryAt <= NOW()}.</li>
 *   <li>{@link OptimisticLockException} is handled gracefully — if another process already
 *       picked up the same payment, we skip it and move on.</li>
 *   <li>When {@code retryCount >= maxRetries}, the payment transitions to
 *       {@link PaymentStatus#FAILED} and a {@code PaymentFailed} outbox event is written.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedRateString = "${payment.retry.scheduler.interval:60000}")
    public void retryDuePayments() {
        List<PaymentEntity> due = paymentRepository
                .findByStatusAndNextRetryAtBefore(PaymentStatus.PENDING_RETRY, Instant.now());

        if (due.isEmpty()) {
            return;
        }

        log.info("Payment retry scheduler: {} payment(s) due for retry", due.size());

        for (PaymentEntity payment : due) {
            try {
                paymentService.retryPayment(payment);
            } catch (OptimisticLockException e) {
                // Another process already picked up this payment — safe to skip
                log.debug("Skipping retry for paymentId='{}': already claimed by another process",
                        payment.getId());
            } catch (Exception e) {
                // Log and continue — one failure must not block retries for other payments
                log.error("Retry attempt failed for paymentId='{}', bookingId='{}': {}",
                        payment.getId(), payment.getBookingId(), e.getMessage());
            }
        }
    }
}

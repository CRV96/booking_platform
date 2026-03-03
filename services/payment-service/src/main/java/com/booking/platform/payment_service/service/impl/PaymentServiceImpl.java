package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.messaging.publisher.PaymentEventPublisher;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Implementation of {@link PaymentService}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Idempotency check — if a payment with this bookingId already exists, return it</li>
 *   <li>Create {@link PaymentEntity} with status {@code INITIATED}, persist</li>
 *   <li>Call {@link PaymentGateway#createPaymentIntent} (outside transaction to avoid holding DB connection)</li>
 *   <li>Update entity with external ID + status {@code PROCESSING}, persist</li>
 *   <li>Call {@link PaymentGateway#confirmPayment} (test mode auto-confirms with pm_card_visa)</li>
 *   <li>On success → status {@code COMPLETED}, persist, publish {@code PaymentCompletedEvent}</li>
 *   <li>On business failure → status {@code FAILED}, persist, publish {@code PaymentFailedEvent}</li>
 *   <li>On infrastructure failure → status {@code PENDING_RETRY} (P4-03 circuit breaker / timeout)</li>
 * </ol>
 *
 * <p>Gateway calls are made outside {@code @Transactional} blocks to avoid holding
 * database connections during potentially slow HTTP calls to Stripe.
 *
 * <p>Gateway methods return {@link java.util.concurrent.CompletableFuture} (required by
 * Resilience4j's {@code @TimeLimiter}). We call {@code .join()} to block, then unwrap
 * the {@link CompletionException} to inspect the root cause.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    public PaymentEntity processPayment(String bookingId, String userId, BigDecimal amount, String currency) {
        // ── Step 1: Idempotency check ──────────────────────────────────────────
        Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(bookingId);
        if (existing.isPresent()) {
            log.info("Duplicate payment request for bookingId='{}' — returning existing payment id='{}'",
                    bookingId, existing.get().getId());
            return existing.get();
        }

        // ── Step 2: Create payment record ──────────────────────────────────────
        PaymentEntity payment = createPaymentRecord(bookingId, userId, amount, currency);
        log.info("Payment INITIATED: id='{}', bookingId='{}', amount={} {}",
                payment.getId(), bookingId, amount, currency);

        // ── Steps 3–8: Gateway interaction + event publishing ──────────────────
        try {
            // Step 3: Create payment intent on gateway (.join() blocks on the future)
            GatewayPaymentResponse createResponse =
                    paymentGateway.createPaymentIntent(amount, currency, bookingId).join();

            // Step 4: Update entity with external ID, move to PROCESSING
            payment = updateToProcessing(payment.getId(), createResponse);
            log.info("Payment PROCESSING: id='{}', externalId='{}'",
                    payment.getId(), createResponse.externalPaymentId());

            // Step 5: Confirm the payment (test mode auto-confirms)
            GatewayPaymentResponse confirmResponse =
                    paymentGateway.confirmPayment(createResponse.externalPaymentId()).join();

            // Step 6: Check result and update accordingly
            if ("succeeded".equals(confirmResponse.status())) {
                payment = markCompleted(payment.getId(), confirmResponse);
                log.info("Payment COMPLETED: id='{}', bookingId='{}'", payment.getId(), bookingId);
                paymentEventPublisher.publishPaymentCompleted(payment);
            } else {
                payment = markFailed(payment.getId(), "Gateway returned status: " + confirmResponse.status());
                log.warn("Payment FAILED (unexpected status): id='{}', status='{}'",
                        payment.getId(), confirmResponse.status());
                paymentEventPublisher.publishPaymentFailed(payment);
            }

        } catch (CompletionException e) {
            // CompletableFuture.join() wraps exceptions in CompletionException — unwrap
            payment = handleGatewayException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            // Direct throw (not wrapped) — gateway unavailable, mark for retry
            payment = markPendingRetry(payment.getId(), e.getMessage());
            log.warn("Payment PENDING_RETRY: id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            // Direct throw (not wrapped) — business failure
            payment = markFailed(payment.getId(), e.getMessage());
            log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
            paymentEventPublisher.publishPaymentFailed(payment);
        }

        return payment;
    }

    // ── Exception handling ────────────────────────────────────────────────────

    private PaymentEntity handleGatewayException(UUID paymentId, String bookingId, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            // Infrastructure failure — gateway down, circuit open, timeout, bulkhead full
            PaymentEntity payment = markPendingRetry(paymentId, cause.getMessage());
            log.warn("Payment PENDING_RETRY (gateway unavailable): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return payment;
        }

        if (cause instanceof PaymentGatewayException) {
            // Business failure — card declined, invalid amount, etc.
            PaymentEntity payment = markFailed(paymentId, cause.getMessage());
            log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            paymentEventPublisher.publishPaymentFailed(payment);
            return payment;
        }

        // Unexpected error
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        PaymentEntity payment = markFailed(paymentId, reason);
        log.error("Payment FAILED (unexpected): id='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
        paymentEventPublisher.publishPaymentFailed(payment);
        return payment;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    @Transactional
    protected PaymentEntity createPaymentRecord(String bookingId, String userId, BigDecimal amount, String currency) {
        PaymentEntity payment = PaymentEntity.builder()
                .bookingId(bookingId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(bookingId)
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity updateToProcessing(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setExternalPaymentId(response.externalPaymentId());
        payment.setPaymentMethod(response.paymentMethod());
        payment.setStatus(PaymentStatus.PROCESSING);
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markCompleted(UUID paymentId, GatewayPaymentResponse response) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentMethod(response.paymentMethod());
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markFailed(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        return paymentRepository.save(payment);
    }

    @Transactional
    protected PaymentEntity markPendingRetry(UUID paymentId, String reason) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.PENDING_RETRY);
        payment.setFailureReason(reason);
        return paymentRepository.save(payment);
    }
}

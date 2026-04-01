package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.payment_service.validation.PaymentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Orchestrates the payment processing flow.
 *
 * <p>This class is intentionally free of {@code @Transactional} — all database
 * state transitions are delegated to {@link PaymentStateTransitionService}, where
 * each method carries its own {@code @Transactional} boundary and runs through the
 * Spring proxy. This avoids the self-invocation trap where {@code @Transactional}
 * annotations on {@code protected} methods in the same bean would be silently ignored.
 *
 * <p>Responsibilities of this class:
 * <ul>
 *   <li>Input validation</li>
 *   <li>Idempotency check</li>
 *   <li>Gateway calls (outside any transaction — network I/O must not hold DB connections)</li>
 *   <li>Routing results to the appropriate state transition</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String GATEWAY_UNEXPECTED_STATUS = "Gateway returned status: ";

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentStateTransitionService transitions;
    private final PaymentValidator paymentValidator;

    @Override
    public PaymentEntity processPayment(String bookingId, String userId, BigDecimal amount, String currency) {
        paymentValidator.validatePaymentForProcessing(bookingId, userId, amount, currency);

        final String normalizedCurrency = currency.toUpperCase(Locale.ROOT);

        Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(bookingId);
        if (existing.isPresent()) {
            log.info("Duplicate payment request for bookingId='{}' — returning existing payment id='{}'",
                    bookingId, existing.get().getId());
            return existing.get();
        }

        PaymentEntity payment = transitions.createPaymentRecord(bookingId, userId, amount, normalizedCurrency);
        log.info("Payment INITIATED: id='{}', bookingId='{}', amount={} {}",
                payment.getId(), bookingId, amount, normalizedCurrency);

        try {
            GatewayPaymentResponse createResponse =
                    paymentGateway.createPaymentIntent(amount, normalizedCurrency, bookingId).join();

            payment = transitions.updateToProcessing(payment.getId(), createResponse);
            log.info("Payment PROCESSING: id='{}', externalId='{}'",
                    payment.getId(), createResponse.externalPaymentId());

            GatewayPaymentResponse confirmResponse =
                    paymentGateway.confirmPayment(createResponse.externalPaymentId()).join();

            if (BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED.equals(confirmResponse.status())) {
                payment = transitions.markCompleted(payment.getId(), confirmResponse);
                log.info("Payment COMPLETED: id='{}', bookingId='{}'", payment.getId(), bookingId);
            } else {
                payment = transitions.markFailed(payment.getId(), GATEWAY_UNEXPECTED_STATUS + confirmResponse.status());
                log.warn("Payment FAILED (unexpected status): id='{}', status='{}'",
                        payment.getId(), confirmResponse.status());
            }

        } catch (CompletionException e) {
            payment = handleGatewayException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            payment = transitions.markPendingRetry(payment.getId(), e.getMessage());
            log.warn("Payment PENDING_RETRY: id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            payment = transitions.markFailed(payment.getId(), e.getMessage());
            log.error("Payment FAILED with PaymentGatewayException for -> id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        }

        return payment;
    }

    @Override
    public void processRefund(String bookingId) {
        paymentValidator.validateBookingId(bookingId);

        Optional<PaymentEntity> optional = paymentRepository.findByBookingId(bookingId);
        if (optional.isEmpty()) {
            log.warn("No payment found for bookingId='{}', cannot process refund", bookingId);
            return;
        }

        PaymentEntity payment = optional.get();

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.info("Payment id='{}' for bookingId='{}' is not COMPLETED (status={}), skipping refund",
                    payment.getId(), bookingId, payment.getStatus());
            return;
        }

        // Re-checks status inside the transaction to prevent concurrent double-refunds
        payment = transitions.markRefundInitiated(payment.getId());
        if (payment.getStatus() != PaymentStatus.REFUND_INITIATED) {
            return;
        }

        log.info("Payment REFUND_INITIATED: id='{}', bookingId='{}'", payment.getId(), bookingId);

        try {
            GatewayRefundResponse refundResponse =
                    paymentGateway.createRefund(payment.getExternalPaymentId(), payment.getAmount()).join();

            if (BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED.equals(refundResponse.status())) {
                payment = transitions.markRefunded(payment.getId(), refundResponse);
                log.info("Payment REFUNDED: id='{}', bookingId='{}', refundId='{}'",
                        payment.getId(), bookingId, refundResponse.refundId());
            } else {
                log.warn("Refund returned unexpected status '{}' for payment id='{}', leaving as REFUND_INITIATED",
                        refundResponse.status(), payment.getId());
            }

        } catch (CompletionException e) {
            handleRefundException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            log.warn("Refund PENDING (gateway unavailable): paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            log.error("Refund FAILED with PaymentGatewayException for -> paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        }
    }

    @Override
    public void retryPayment(PaymentEntity snapshot) {
        PaymentEntity payment = transitions.incrementRetryCount(snapshot.getId());

        log.info("Payment RETRY attempt {}/{}: id='{}', bookingId='{}'",
                payment.getRetryCount(), payment.getMaxRetries(), payment.getId(), payment.getBookingId());

        try {
            GatewayPaymentResponse confirmResponse;

            if (payment.getExternalPaymentId() == null) {
                GatewayPaymentResponse createResponse =
                        paymentGateway.createPaymentIntent(payment.getAmount(), payment.getCurrency(), payment.getBookingId()).join();
                transitions.updateToProcessing(payment.getId(), createResponse);
                confirmResponse = paymentGateway.confirmPayment(createResponse.externalPaymentId()).join();
            } else {
                confirmResponse = paymentGateway.confirmPayment(payment.getExternalPaymentId()).join();
            }

            if (BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED.equals(confirmResponse.status())) {
                transitions.markCompleted(payment.getId(), confirmResponse);
                log.info("Payment COMPLETED (after retry): id='{}', bookingId='{}'",
                        payment.getId(), payment.getBookingId());
            } else {
                transitions.markFailed(payment.getId(), GATEWAY_UNEXPECTED_STATUS + confirmResponse.status());
                log.warn("Payment FAILED after retry (unexpected status): id='{}', status='{}'",
                        payment.getId(), confirmResponse.status());
            }

        } catch (CompletionException e) {
            handleRetryGatewayException(payment, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            handleRetryGatewayException(payment, e);
        } catch (PaymentGatewayException e) {
            transitions.markFailed(payment.getId(), e.getMessage());
            log.error("Payment FAILED with PaymentGatewayException: id='{}', bookingId='{}', reason='{}'",
                    payment.getId(), payment.getBookingId(), e.getMessage());
        }
    }

    private PaymentEntity handleGatewayException(UUID paymentId, String bookingId, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            PaymentEntity payment = transitions.markPendingRetry(paymentId, cause.getMessage());
            log.warn("Payment PENDING_RETRY (gateway unavailable): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return payment;
        }
        if (cause instanceof PaymentGatewayException) {
            PaymentEntity payment = transitions.markFailed(paymentId, cause.getMessage());
            log.error("Payment FAILED (gateway error): id='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, cause.getMessage());
            return payment;
        }
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        PaymentEntity payment = transitions.markFailed(paymentId, reason);
        log.error("Payment FAILED (unexpected): id='{}', bookingId='{}', reason='{}'",
                paymentId, bookingId, reason);
        return payment;
    }

    private void handleRefundException(UUID paymentId, String bookingId, Throwable cause) {
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        if (cause instanceof PaymentGatewayUnavailableException || cause instanceof PaymentGatewayException) {
            log.error("Refund FAILED (gateway error): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, reason);
        } else {
            log.error("Refund FAILED (unexpected): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, reason);
        }
    }

    private void handleRetryGatewayException(PaymentEntity payment, Throwable cause) {
        if (cause instanceof PaymentGatewayUnavailableException) {
            if (payment.getRetryCount() >= payment.getMaxRetries()) {
                transitions.markFailed(payment.getId(), "Max retries exhausted: " + cause.getMessage());
                log.warn("Payment FAILED (max retries exhausted): id='{}', bookingId='{}', attempts={}",
                        payment.getId(), payment.getBookingId(), payment.getRetryCount());
            } else {
                transitions.markPendingRetry(payment.getId(), cause.getMessage());
                log.warn("Payment PENDING_RETRY (attempt {}/{}): id='{}', bookingId='{}'",
                        payment.getRetryCount(), payment.getMaxRetries(),
                        payment.getId(), payment.getBookingId());
            }
            return;
        }
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        transitions.markFailed(payment.getId(), reason);
        log.error("Payment FAILED during retry: id='{}', bookingId='{}', reason='{}'",
                payment.getId(), payment.getBookingId(), reason);
    }
}

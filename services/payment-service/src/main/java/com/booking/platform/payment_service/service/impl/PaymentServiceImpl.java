package com.booking.platform.payment_service.service.impl;

import com.booking.platform.payment_service.constants.BkgConstants;
import com.booking.platform.payment_service.dto.GatewayPaymentResponse;
import com.booking.platform.payment_service.dto.GatewayRefundResponse;
import com.booking.platform.payment_service.dto.PaymentIntentResult;
import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.exception.PaymentGatewayException;
import com.booking.platform.payment_service.exception.PaymentGatewayUnavailableException;
import com.booking.platform.payment_service.gateway.PaymentGateway;
import com.booking.platform.payment_service.repository.PaymentRepository;
import com.booking.platform.payment_service.service.PaymentService;
import com.booking.platform.payment_service.util.PaymentStatusUtil;
import com.booking.platform.payment_service.validation.PaymentValidator;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.common.logging.LogErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
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

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentStateTransitionService transitions;
    private final PaymentValidator paymentValidator;

    @Override
    public PaymentIntentResult getOrCreateOrderPaymentIntent(String orderId, String userId,
                                                             List<String> bookingIds, BigDecimal amount, String currency) {
        paymentValidator.validatePaymentForProcessing(orderId, userId, amount, currency);
        final String normalizedCurrency = currency.toUpperCase(Locale.ROOT);

        Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(orderId);
        if (existing.isPresent()) {
            return resolveExistingIntent(existing.get());
        }
        return createNewOrderIntent(orderId, userId, bookingIds, amount, normalizedCurrency);
    }

    private PaymentIntentResult createNewOrderIntent(String orderId, String userId, List<String> bookingIds,
                                                     BigDecimal amount, String currency) {
        PaymentEntity payment = transitions.createOrderPaymentRecord(orderId, userId, bookingIds, amount, currency);

        // One PaymentIntent for the whole order; the customer confirms it client-side.
        GatewayPaymentResponse createResponse =
                paymentGateway.createPaymentIntent(amount, currency, orderId).join();
        payment = transitions.updateToProcessing(payment.getId(), createResponse);

        ApplicationLogger.logMessage(log, Level.INFO,
                "Order payment intent created: id='{}', orderId='{}', bookings={}, externalId='{}'",
                payment.getId(), orderId, bookingIds, createResponse.externalPaymentId());
        return toResult(payment, createResponse.clientSecret());
    }

    private PaymentIntentResult resolveExistingIntent(PaymentEntity payment) {
        // Already resolved (paid or refund lifecycle) — no card entry needed. Don't call the gateway;
        // return status with no secret so the caller routes to the confirmation page.
        if (PaymentStatusUtil.isResolved(payment.getStatus())) {
            ApplicationLogger.logMessage(log, Level.INFO,
                    "Payment intent already resolved for bookingId='{}': status={}",
                    payment.getBookingId(), payment.getStatus());
            return toResult(payment, null);
        }

        // Interrupted creation (record exists but no PaymentIntent was recorded) — finish it now.
        if (payment.getExternalPaymentId() == null) {
            GatewayPaymentResponse createResponse =
                    paymentGateway.createPaymentIntent(payment.getAmount(), payment.getCurrency(), payment.getBookingId()).join();
            payment = transitions.updateToProcessing(payment.getId(), createResponse);
            return toResult(payment, createResponse.clientSecret());
        }

        // Still awaiting payment (e.g. page reload) — retrieve a fresh client secret; never stored.
        GatewayPaymentResponse retrieveResponse =
                paymentGateway.retrievePaymentIntent(payment.getExternalPaymentId()).join();
        ApplicationLogger.logMessage(log, Level.INFO,
                "Payment intent retrieved for bookingId='{}': externalId='{}'",
                payment.getBookingId(), payment.getExternalPaymentId());
        return toResult(payment, retrieveResponse.clientSecret());
    }

    private PaymentIntentResult toResult(PaymentEntity payment, String clientSecret) {
        return new PaymentIntentResult(
                payment.getId().toString(),
                payment.getBookingId(),
                payment.getExternalPaymentId(),
                clientSecret,
                payment.getStatus().name());
    }

    @Override
    public void processRefund(String bookingId) {
        paymentValidator.validateBookingId(bookingId);

        Optional<PaymentEntity> optional = paymentRepository.findByBookingId(bookingId);
        if (optional.isEmpty()) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.PAYMENT_REFUND_FAILED,
                    "No payment found for bookingId='{}', cannot process refund", bookingId);
            return;
        }

        PaymentEntity payment = optional.get();

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            ApplicationLogger.logMessage(log, Level.INFO,
                    "Payment id='{}' for bookingId='{}' is not COMPLETED (status={}), skipping refund",
                    payment.getId(), bookingId, payment.getStatus());
            return;
        }

        // Re-checks status inside the transaction to prevent concurrent double-refunds
        payment = transitions.markRefundInitiated(payment.getId());
        if (payment.getStatus() != PaymentStatus.REFUND_INITIATED) {
            return;
        }

        ApplicationLogger.logMessage(log, Level.INFO, "Payment REFUND_INITIATED: id='{}', bookingId='{}'", payment.getId(), bookingId);

        try {
            GatewayRefundResponse refundResponse =
                    paymentGateway.createRefund(payment.getExternalPaymentId(), payment.getAmount()).join();

            if (BkgConstants.BkgStripeConstants.RESPONSE_SUCCEEDED.equals(refundResponse.status())) {
                payment = transitions.markRefunded(payment.getId(), refundResponse);
                ApplicationLogger.logMessage(log, Level.INFO, "Payment REFUNDED: id='{}', bookingId='{}', refundId='{}'",
                        payment.getId(), bookingId, refundResponse.refundId());
            } else {
                ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.PAYMENT_REFUND_FAILED,
                        "Refund returned unexpected status '{}' for payment id='{}', leaving as REFUND_INITIATED",
                        refundResponse.status(), payment.getId());
            }

        } catch (CompletionException e) {
            handleRefundException(payment.getId(), bookingId, e.getCause());
        } catch (PaymentGatewayUnavailableException e) {
            ApplicationLogger.logMessage(log, Level.WARN, LogErrorCode.PAYMENT_GATEWAY_UNAVAILABLE,
                    "Refund PENDING (gateway unavailable): paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        } catch (PaymentGatewayException e) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.PAYMENT_REFUND_FAILED,
                    "Refund FAILED with PaymentGatewayException for -> paymentId='{}', bookingId='{}', reason='{}'",
                    payment.getId(), bookingId, e.getMessage());
        }
    }

    private void handleRefundException(UUID paymentId, String bookingId, Throwable cause) {
        String reason = cause != null ? cause.getMessage() : "Unknown error";
        if (cause instanceof PaymentGatewayUnavailableException || cause instanceof PaymentGatewayException) {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.PAYMENT_REFUND_FAILED,
                    "Refund FAILED (gateway error): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, reason);
        } else {
            ApplicationLogger.logMessage(log, Level.ERROR, LogErrorCode.PAYMENT_REFUND_FAILED,
                    "Refund FAILED (unexpected): paymentId='{}', bookingId='{}', reason='{}'",
                    paymentId, bookingId, reason);
        }
    }

}

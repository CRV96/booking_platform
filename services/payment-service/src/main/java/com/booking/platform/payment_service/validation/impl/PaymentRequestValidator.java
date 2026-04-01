package com.booking.platform.payment_service.validation.impl;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.validation.PaymentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Default implementation of {@link PaymentValidator}.
 *
 * <p>Validates payment request fields at the service boundary before any
 * database or gateway interaction takes place. All rules throw
 * {@link IllegalArgumentException} on violation so the caller can reject
 * the request without leaving any partial state in the system.
 */
@Component
@Slf4j
public class PaymentRequestValidator implements PaymentValidator {

    @Override
    public void validateBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("Booking ID must not be null or blank, got: " + bookingId);
        }

        log.debug("Booking ID '{}' is valid", bookingId);
    }

    @Override
    public void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank, got: " + userId);
        }

        log.debug("User ID '{}' is valid", userId);
    }

    @Override
    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive, got: " + amount);
        }

        log.debug("Payment amount {} is valid", amount);
    }

    @Override
    public void validateCurrency(String currency) {
        if (currency == null || currency.isBlank() || currency.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-character ISO 4217 code, got: " + currency);
        }

        log.debug("Currency code '{}' is valid", currency);
    }

    @Override
    public void validatePaymentForProcessing(String bookingId, String userId, BigDecimal amount, String currency) {
        log.debug("Validating payment request: bookingId='{}', userId='{}', amount={}, currency='{}'",
                bookingId, userId, amount, currency);

        validateBookingId(bookingId);
        validateUserId(userId);
        validateAmount(amount);
        validateCurrency(currency);

        log.debug("Payment request validation passed for bookingId='{}'", bookingId);
    }

    @Override
    public void assertValidTransition(PaymentEntity payment, PaymentStatus target) {
        if (!payment.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException(String.format(
                    "Invalid status transition for payment id='%s': %s → %s",
                    payment.getId(), payment.getStatus(), target));
        }
        log.debug("Valid status transition for payment id='{}': {} → {}",
                payment.getId(), payment.getStatus(), target);
    }
}

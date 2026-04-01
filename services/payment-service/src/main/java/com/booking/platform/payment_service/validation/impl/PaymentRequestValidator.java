package com.booking.platform.payment_service.validation.impl;

import com.booking.platform.payment_service.validation.PaymentValidator;
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
public class PaymentRequestValidator implements PaymentValidator {

    @Override
    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive, got: " + amount);
        }
    }

    @Override
    public String validateAndNormalizeCurrency(String currency) {
        if (currency == null || currency.isBlank() || currency.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-character ISO 4217 code, got: " + currency);
        }
        return currency.toUpperCase();
    }
}

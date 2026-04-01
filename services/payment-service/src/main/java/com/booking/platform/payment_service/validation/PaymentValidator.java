package com.booking.platform.payment_service.validation;

import java.math.BigDecimal;

/**
 * Contract for validating and normalizing payment request inputs.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Rejecting null or non-positive amounts</li>
 *   <li>Rejecting malformed currency codes and normalizing valid ones to uppercase</li>
 * </ul>
 *
 * <p>Validators throw {@link IllegalArgumentException} when a rule is violated,
 * which propagates up to the caller without touching any database state.
 */
public interface PaymentValidator {

    /**
     * Validates that {@code amount} is non-null and strictly positive.
     *
     * @param amount the payment amount to validate
     * @throws IllegalArgumentException if {@code amount} is null or {@code <= 0}
     */
    void validateAmount(BigDecimal amount);

    /**
     * Validates that {@code currency} is a 3-character ISO 4217 code and
     * returns its uppercase form.
     *
     * @param currency the raw currency string from the caller
     * @return the uppercase-normalized currency code (e.g. {@code "usd"} → {@code "USD"})
     * @throws IllegalArgumentException if {@code currency} is null, blank, or not 3 characters
     */
    String validateAndNormalizeCurrency(String currency);
}

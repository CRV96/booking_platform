package com.booking.platform.payment_service.validation;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import java.math.BigDecimal;

/**
 * Contract for validating payment request inputs and enforcing state-machine rules.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Rejecting null or blank booking/user identifiers</li>
 *   <li>Rejecting null or non-positive amounts</li>
 *   <li>Rejecting malformed currency codes (normalization is the caller's responsibility)</li>
 *   <li>Asserting that payment status transitions are permitted by the state machine</li>
 * </ul>
 *
 * <p>Validators throw {@link IllegalArgumentException} for invalid input and
 * {@link IllegalStateException} for invalid state transitions — both propagate
 * to the caller without touching any database state.
 */
public interface PaymentValidator {

    /**
     * Validates that {@code bookingId} is non-null and non-blank.
     *
     * @param bookingId the booking identifier to validate
     * @throws IllegalArgumentException if {@code bookingId} is null or blank
     */
    void validateBookingId(String bookingId);

    /**
     * Validates that {@code userId} is non-null and non-blank.
     *
     * @param userId the user identifier to validate
     * @throws IllegalArgumentException if {@code userId} is null or blank
     */
    void validateUserId(String userId);

    /**
     * Validates that {@code amount} is non-null and strictly positive.
     *
     * @param amount the payment amount to validate
     * @throws IllegalArgumentException if {@code amount} is null or {@code <= 0}
     */
    void validateAmount(BigDecimal amount);

    /**
     * Validates that {@code currency} is a 3-character ISO 4217 code.
     * Normalization (uppercase) is the caller's responsibility via {@link String#toUpperCase(java.util.Locale)}.
     *
     * @param currency the currency string to validate
     * @throws IllegalArgumentException if {@code currency} is null, blank, or not 3 characters
     */
    void validateCurrency(String currency);

    /**
     * Convenience method that runs all four field validations in order:
     * bookingId → userId → amount → currency.
     *
     * @param bookingId the booking identifier
     * @param userId    the user identifier
     * @param amount    the payment amount
     * @param currency  the payment currency
     * @throws IllegalArgumentException on the first validation rule that is violated
     */
    void validatePaymentForProcessing(String bookingId, String userId, BigDecimal amount, String currency);

    /**
     * Asserts that transitioning {@code payment} to {@code target} is permitted
     * by the {@link PaymentStatus} state machine.
     *
     * @param payment the payment whose current status is checked
     * @param target  the intended next status
     * @throws IllegalStateException if the transition is not allowed
     */
    void assertValidTransition(PaymentEntity payment, PaymentStatus target);
}

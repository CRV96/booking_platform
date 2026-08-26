package com.booking.platform.payment_service.dto;

/**
 * Result of {@code getOrCreatePaymentIntent} — everything the checkout page needs to
 * confirm a payment client-side, plus enough state to route the user correctly.
 * @param paymentId         our internal PaymentEntity id
 * @param bookingId         the booking this payment is for
 * @param externalPaymentId the gateway's PaymentIntent id (safe to store/return; not a secret)
 * @param clientSecret      one-time secret for client-side confirmation; {@code null} when not applicable
 * @param status            our {@link com.booking.platform.payment_service.entity.enums.PaymentStatus} name
 */
public record PaymentIntentResult(
        String paymentId,
        String bookingId,
        String externalPaymentId,
        String clientSecret,
        String status
) {}

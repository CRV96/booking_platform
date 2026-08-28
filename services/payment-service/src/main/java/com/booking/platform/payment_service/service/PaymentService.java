package com.booking.platform.payment_service.service;

import com.booking.platform.payment_service.dto.PaymentIntentResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Core payment service — creating payment intents for checkout (client-side confirmation) and
 * processing refunds. The actual charge is confirmed client-side (Stripe Elements) or by the
 * mock confirm endpoint; the outcome arrives via the Stripe webhook / mock and is applied by
 * {@link com.booking.platform.payment_service.service.impl.PaymentOutcomeService}.
 */
public interface PaymentService {

    /**
     * Get-or-create the payment intent for an <b>order</b> covering one or more bookings, for
     * client-side confirmation (checkout).
     *
     * <p><b>Create-only</b> — it does not confirm the charge; the customer confirms client-side with
     * their own card. Idempotent on {@code orderId}: one PaymentIntent covers the whole order
     * (summed amount), and the payment records all booking IDs so the completion/failure events
     * confirm/cancel every booking. A repeat call (e.g. a checkout reload) retrieves the existing
     * intent's fresh {@code clientSecret}; an already-resolved order returns its status with a
     * {@code null} client secret so the caller can route to the confirmation page.
     *
     * @param orderId    client-generated order id (idempotency key)
     * @param userId     Keycloak subject of the user
     * @param bookingIds the bookings this order pays for
     * @param amount     the order total (must be positive)
     * @param currency   ISO 4217 currency code
     */
    PaymentIntentResult getOrCreateOrderPaymentIntent(String orderId, String userId, List<String> bookingIds,
                                                      BigDecimal amount, String currency);

    /**
     * Processes a refund for a completed payment.
     *
     * <p>Finds the payment by bookingId. If the payment is not in COMPLETED status,
     * logs and returns (no refund needed). Otherwise:
     * <ol>
     *   <li>COMPLETED → REFUND_INITIATED (persisted atomically with status re-check)</li>
     *   <li>Calls gateway.createRefund() outside transaction</li>
     *   <li>On success: REFUND_INITIATED → REFUNDED + outbox event "RefundCompleted"</li>
     *   <li>On gateway unavailable: stays REFUND_INITIATED for manual retry</li>
     * </ol>
     *
     * @param bookingId the booking whose payment should be refunded
     */
    void processRefund(String bookingId);
}

package com.booking.platform.payment_service.util;

import com.booking.platform.payment_service.entity.enums.PaymentStatus;

/**
 * Small helpers for reasoning about {@link PaymentStatus}.
 */
public final class PaymentStatusUtil {

    private PaymentStatusUtil() {
        // Utility class — not instantiable
    }

    /**
     * Returns {@code true} when the payment's outcome is already decided, so no further
     * payment outcome (success/failure) or new card entry should be applied.
     *
     * <p>Covers the terminal states ({@code COMPLETED}, {@code FAILED}, {@code REFUNDED})
     * and {@code REFUND_INITIATED} (a refund is already underway). Used to make duplicate
     * or racing signals idempotent no-ops in the checkout and outcome flows.
     */
    public static boolean isResolved(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.REFUND_INITIATED
                || status == PaymentStatus.REFUNDED;
    }
}

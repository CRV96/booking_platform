package com.booking.platform.payment_service.util;

import java.math.BigDecimal;

/**
 * Money conversion helpers.
 */
public final class MoneyUtil {

    private MoneyUtil() {
        // Utility class — not instantiable
    }

    private static final BigDecimal MINOR_UNIT_FACTOR = BigDecimal.valueOf(100);

    /**
     * Converts an amount in a currency's major unit (e.g. {@code 49.99} dollars) to its
     * minor unit as an exact whole number ({@code 4999} cents) — the format Stripe requires.
     *
     * <p>Assumes a 2-decimal ("×100") currency, which covers USD/EUR/GBP and most others.
     * Zero-decimal (JPY) or three-decimal (BHD, KWD) currencies would need a per-currency
     * exponent — revisit if such currencies are ever supported.
     *
     * @param amount the amount in major units (e.g. dollars)
     * @return the amount in minor units (e.g. cents)
     * @throws ArithmeticException if the amount has a fractional minor unit (e.g. {@code 1.005})
     */
    public static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNIT_FACTOR).longValueExact();
    }
}

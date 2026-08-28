package com.booking.platform.payment_service.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUtilTest {

    @Test
    void toMinorUnits_convertsDollarsToCents() {
        assertThat(MoneyUtil.toMinorUnits(new BigDecimal("49.99"))).isEqualTo(4999L);
    }

    @Test
    void toMinorUnits_wholeAmount() {
        assertThat(MoneyUtil.toMinorUnits(new BigDecimal("50"))).isEqualTo(5000L);
    }

    @Test
    void toMinorUnits_zero() {
        assertThat(MoneyUtil.toMinorUnits(BigDecimal.ZERO)).isEqualTo(0L);
    }

    @Test
    void toMinorUnits_fractionalCent_throws() {
        // 1.005 → 100.5 cents is not a whole number
        assertThatThrownBy(() -> MoneyUtil.toMinorUnits(new BigDecimal("1.005")))
                .isInstanceOf(ArithmeticException.class);
    }
}

package com.booking.platform.payment_service.util;

import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusUtilTest {

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "FAILED", "REFUND_INITIATED", "REFUNDED"})
    void resolvedStatuses_areResolved(PaymentStatus status) {
        assertThat(PaymentStatusUtil.isResolved(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"INITIATED", "PROCESSING", "PENDING_RETRY"})
    void inProgressStatuses_areNotResolved(PaymentStatus status) {
        assertThat(PaymentStatusUtil.isResolved(status)).isFalse();
    }
}

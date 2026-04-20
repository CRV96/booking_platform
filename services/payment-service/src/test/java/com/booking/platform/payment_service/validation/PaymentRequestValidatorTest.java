package com.booking.platform.payment_service.validation;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import com.booking.platform.payment_service.validation.impl.PaymentRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestValidatorTest {

    private PaymentRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentRequestValidator();
    }

    // ── validateBookingId ─────────────────────────────────────────────────────

    @Test
    void validateBookingId_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateBookingId("booking-1")).doesNotThrowAnyException();
    }

    @Test
    void validateBookingId_null_throws() {
        assertThatThrownBy(() -> validator.validateBookingId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Booking ID");
    }

    @Test
    void validateBookingId_blank_throws() {
        assertThatThrownBy(() -> validator.validateBookingId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Booking ID");
    }

    @Test
    void validateBookingId_empty_throws() {
        assertThatThrownBy(() -> validator.validateBookingId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── validateUserId ────────────────────────────────────────────────────────

    @Test
    void validateUserId_valid_doesNotThrow() {
        assertThatCode(() -> validator.validateUserId("user-1")).doesNotThrowAnyException();
    }

    @Test
    void validateUserId_null_throws() {
        assertThatThrownBy(() -> validator.validateUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    void validateUserId_blank_throws() {
        assertThatThrownBy(() -> validator.validateUserId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    // ── validateAmount ────────────────────────────────────────────────────────

    @Test
    void validateAmount_positive_doesNotThrow() {
        assertThatCode(() -> validator.validateAmount(new BigDecimal("99.99"))).doesNotThrowAnyException();
    }

    @Test
    void validateAmount_null_throws() {
        assertThatThrownBy(() -> validator.validateAmount(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void validateAmount_zero_throws() {
        assertThatThrownBy(() -> validator.validateAmount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void validateAmount_negative_throws() {
        assertThatThrownBy(() -> validator.validateAmount(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void validateAmount_smallPositive_doesNotThrow() {
        assertThatCode(() -> validator.validateAmount(new BigDecimal("0.01"))).doesNotThrowAnyException();
    }

    // ── validateCurrency ──────────────────────────────────────────────────────

    @Test
    void validateCurrency_validCode_doesNotThrow() {
        assertThatCode(() -> validator.validateCurrency("USD")).doesNotThrowAnyException();
    }

    @Test
    void validateCurrency_null_throws() {
        assertThatThrownBy(() -> validator.validateCurrency(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void validateCurrency_blank_throws() {
        assertThatThrownBy(() -> validator.validateCurrency("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void validateCurrency_tooShort_throws() {
        assertThatThrownBy(() -> validator.validateCurrency("US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void validateCurrency_tooLong_throws() {
        assertThatThrownBy(() -> validator.validateCurrency("USDT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    // ── validatePaymentForProcessing ──────────────────────────────────────────

    @Test
    void validatePaymentForProcessing_allValid_doesNotThrow() {
        assertThatCode(() -> validator.validatePaymentForProcessing(
                "booking-1", "user-1", new BigDecimal("99.99"), "USD"))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePaymentForProcessing_invalidBookingId_throws() {
        assertThatThrownBy(() -> validator.validatePaymentForProcessing(
                null, "user-1", new BigDecimal("99.99"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatePaymentForProcessing_invalidAmount_throws() {
        assertThatThrownBy(() -> validator.validatePaymentForProcessing(
                "booking-1", "user-1", BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatePaymentForProcessing_invalidCurrency_throws() {
        assertThatThrownBy(() -> validator.validatePaymentForProcessing(
                "booking-1", "user-1", new BigDecimal("99.99"), "XX"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── assertValidTransition ─────────────────────────────────────────────────

    @Test
    void assertValidTransition_validTransition_doesNotThrow() {
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.INITIATED)
                .build();

        assertThatCode(() -> validator.assertValidTransition(payment, PaymentStatus.PROCESSING))
                .doesNotThrowAnyException();
    }

    @Test
    void assertValidTransition_invalidTransition_throwsIllegalStateException() {
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.COMPLETED)
                .build();

        assertThatThrownBy(() -> validator.assertValidTransition(payment, PaymentStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("PROCESSING");
    }

    @Test
    void assertValidTransition_terminalState_throwsIllegalStateException() {
        PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .status(PaymentStatus.FAILED)
                .build();

        assertThatThrownBy(() -> validator.assertValidTransition(payment, PaymentStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    void assertValidTransition_messageContainsPaymentId() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PaymentEntity payment = PaymentEntity.builder()
                .id(id)
                .status(PaymentStatus.REFUNDED)
                .build();

        assertThatThrownBy(() -> validator.assertValidTransition(payment, PaymentStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(id.toString());
    }
}

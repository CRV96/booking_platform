package com.booking.platform.booking_service.validation;

import com.booking.platform.booking_service.dto.AddCartItemDto;
import com.booking.platform.booking_service.validation.impl.CartValidationImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartValidationImplTest {

    private final CartValidationImpl validation = new CartValidationImpl();

    private AddCartItemDto.AddCartItemDtoBuilder valid() {
        return AddCartItemDto.builder()
                .userId("user-1")
                .eventId("event-1")
                .eventTitle("Rock Fest")
                .seatCategory("VIP")
                .quantity(2)
                .unitPrice(new BigDecimal("49.99"))
                .currency("USD");
    }

    @Test
    void validate_valid_passes() {
        assertThatCode(() -> validation.validate(valid().build())).doesNotThrowAnyException();
    }

    @Test
    void validate_blankEventId_throws() {
        assertThatThrownBy(() -> validation.validate(valid().eventId(" ").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("event_id");
    }

    @Test
    void validate_blankEventTitle_throws() {
        assertThatThrownBy(() -> validation.validate(valid().eventTitle("").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("event_title");
    }

    @Test
    void validate_blankSeatCategory_throws() {
        assertThatThrownBy(() -> validation.validate(valid().seatCategory("").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seat_category");
    }

    @Test
    void validate_blankCurrency_throws() {
        assertThatThrownBy(() -> validation.validate(valid().currency("").build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currency");
    }

    @Test
    void validate_zeroQuantity_throws() {
        assertThatThrownBy(() -> validation.validate(valid().quantity(0).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    @Test
    void validate_nullUnitPrice_throws() {
        assertThatThrownBy(() -> validation.validate(valid().unitPrice(null).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit_price");
    }

    @Test
    void validate_negativeUnitPrice_throws() {
        assertThatThrownBy(() -> validation.validate(valid().unitPrice(new BigDecimal("-1")).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit_price");
    }

    @Test
    void validateQuantity_positive_passes() {
        assertThatCode(() -> validation.validateQuantity(1)).doesNotThrowAnyException();
    }

    @Test
    void validateQuantity_zeroOrNegative_throws() {
        assertThatThrownBy(() -> validation.validateQuantity(0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        assertThatThrownBy(() -> validation.validateQuantity(-3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

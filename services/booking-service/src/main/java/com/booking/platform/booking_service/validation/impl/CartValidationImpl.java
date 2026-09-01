package com.booking.platform.booking_service.validation.impl;

import com.booking.platform.booking_service.constants.FieldConst;
import com.booking.platform.booking_service.dto.AddCartItemDto;
import com.booking.platform.booking_service.validation.CartValidation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Field-level validation for cart operations.
 */
@Component
public class CartValidationImpl implements CartValidation {

    @Override
    public void validate(AddCartItemDto dto) {
        requireText(dto.eventId(), FieldConst.EVENT_ID);
        requireText(dto.eventTitle(), FieldConst.EVENT_TITLE);
        requireText(dto.seatCategory(), FieldConst.SEAT_CATEGORY);
        requireText(dto.currency(), FieldConst.CURRENCY);
        validateQuantity(dto.quantity());
        requireNonNegative(dto.unitPrice(), FieldConst.UNIT_PRICE);
    }

    @Override
    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(FieldConst.QUANTITY + " must be greater than 0");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must be zero or greater");
        }
    }
}

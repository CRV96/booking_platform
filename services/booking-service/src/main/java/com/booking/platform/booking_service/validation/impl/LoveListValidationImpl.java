package com.booking.platform.booking_service.validation.impl;

import com.booking.platform.booking_service.constants.FieldConst;
import com.booking.platform.booking_service.validation.LoveListValidation;
import org.springframework.stereotype.Component;

/**
 * Field-level validation for lovelist operations.
 */
@Component
public class LoveListValidationImpl implements LoveListValidation {

    @Override
    public void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(FieldConst.EVENT_ID + " is required");
        }
    }
}

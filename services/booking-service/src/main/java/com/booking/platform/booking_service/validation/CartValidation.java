package com.booking.platform.booking_service.validation;

import com.booking.platform.booking_service.dto.AddCartItemDto;

/**
 * Validates cart operations. Failures throw {@link IllegalArgumentException},
 * mapped to gRPC {@code INVALID_ARGUMENT} by the shared exception interceptor.
 */
public interface CartValidation {

    /** Validates a request to add/upsert a cart line. */
    void validate(AddCartItemDto dto);

    /** Validates a requested cart-line quantity (must be > 0). */
    void validateQuantity(int quantity);
}

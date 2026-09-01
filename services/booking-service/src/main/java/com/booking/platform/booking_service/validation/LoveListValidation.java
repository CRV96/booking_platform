package com.booking.platform.booking_service.validation;

/**
 * Validates lovelist operations. Failures throw {@link IllegalArgumentException},
 * mapped to gRPC {@code INVALID_ARGUMENT} by the shared exception interceptor.
 */
public interface LoveListValidation {

    /** Validates that an event id is present. */
    void validateEventId(String eventId);
}

package com.booking.platform.booking_service.util;

import com.booking.platform.common.grpc.context.GrpcUserContext;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Small helpers for handling gRPC requests in booking-service: resolving the
 * authenticated user from the JWT context and parsing string fields into typed values.
 *
 * <p>All parse failures throw {@link IllegalArgumentException}, which the shared
 * {@code GrpcExceptionInterceptor} maps to gRPC {@code INVALID_ARGUMENT}.
 */
public final class GrpcRequestUtils {

    private GrpcRequestUtils() {}

    /** Returns the authenticated user ID from the gRPC context, or throws if absent. */
    public static String requireUserId() {
        String userId = GrpcUserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is required");
        }
        return userId;
    }

    /** Parses a required UUID field, throwing a clear error if blank or malformed. */
    public static UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID: " + value);
        }
    }

    /** Parses a required decimal field (money as string), throwing a clear error if blank or malformed. */
    public static BigDecimal parseDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid decimal: " + value);
        }
    }
}

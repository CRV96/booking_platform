package com.booking.platform.graphql_gateway.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    /**
     * ============== USER - SERVICE ERRORS ===============
     */
    // Authentication errors
    INVALID_CREDENTIALS("AUTH_001", "Invalid username or password"),
    USER_ALREADY_EXISTS("AUTH_002", "User with this email already exists"),
    INVALID_TOKEN("AUTH_003", "Invalid or expired token"),
    TOKEN_EXPIRED("AUTH_004", "Token has expired"),

    // User errors
    USER_NOT_FOUND("USER_001", "User not found"),
    USER_DISABLED("USER_002", "User account is disabled"),

    // Authorization errors
    FORBIDDEN("AUTHZ_001", "You don't have permission to perform this action"),
    UNAUTHENTICATED("AUTHZ_002", "Authentication required"),

    // Validation errors
    VALIDATION_ERROR("VAL_001", "Input validation failed"),
    INVALID_INPUT("VAL_002", "Invalid input provided"),

    // Rate limiting errors
    RATE_LIMITED("RATE_001", "Too many requests. Please try again later."),

    // General errors
    INTERNAL_ERROR("GEN_001", "An unexpected error occurred"),
    SERVICE_UNAVAILABLE("GEN_002", "Service is temporarily unavailable"),
    NOT_IMPLEMENTED("GEN_003", "This feature is not yet implemented"),
    NOT_FOUND("GEN_004", "The requested resource was not found");
    /**
     * ==============================================
     */


    private final String code;
    private final String defaultMessage;
}

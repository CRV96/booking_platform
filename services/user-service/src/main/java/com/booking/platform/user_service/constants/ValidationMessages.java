package com.booking.platform.user_service.constants;

/**
 * Message keys for validation errors, resolved via Spring {@link org.springframework.context.MessageSource}.
 * Actual messages are in {@code messages.properties} (English) and {@code messages_ro.properties} (Romanian).
 */
public interface ValidationMessages {

    // Email
    String EMAIL_REQUIRED = "validation.email.required";
    String EMAIL_TOO_LONG = "validation.email.too-long";
    String EMAIL_INVALID_FORMAT = "validation.email.invalid-format";

    // Password
    String PASSWORD_REQUIRED = "validation.password.required";
    String PASSWORD_TOO_SHORT = "validation.password.too-short";
    String PASSWORD_TOO_LONG = "validation.password.too-long";

    // Names
    String FIRST_NAME_REQUIRED = "validation.first-name.required";
    String FIRST_NAME_TOO_LONG = "validation.first-name.too-long";
    String LAST_NAME_REQUIRED = "validation.last-name.required";
    String LAST_NAME_TOO_LONG = "validation.last-name.too-long";

    // Other fields
    String USERNAME_REQUIRED = "validation.username.required";
    String USER_ID_REQUIRED = "validation.user-id.required";
    String REFRESH_TOKEN_REQUIRED = "validation.refresh-token.required";
}

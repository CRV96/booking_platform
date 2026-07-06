package com.booking.platform.user_service.constants;

/**
 * Message keys for validation errors, resolved via Spring {@link org.springframework.context.MessageSource}.
 * Actual messages are in {@code messages.properties} (English) and locale-specific variants.
 */
public final class ValidationMessages {

    private ValidationMessages() {}

    // Email
    public static final String EMAIL_REQUIRED = "validation.email.required";
    public static final String EMAIL_TOO_LONG = "validation.email.too-long";
    public static final String EMAIL_INVALID_FORMAT = "validation.email.invalid-format";

    // Password
    public static final String PASSWORD_REQUIRED = "validation.password.required";
    public static final String PASSWORD_TOO_SHORT = "validation.password.too-short";
    public static final String PASSWORD_TOO_LONG = "validation.password.too-long";

    // Names
    public static final String FIRST_NAME_REQUIRED = "validation.first-name.required";
    public static final String FIRST_NAME_TOO_LONG = "validation.first-name.too-long";
    public static final String LAST_NAME_REQUIRED = "validation.last-name.required";
    public static final String LAST_NAME_TOO_LONG = "validation.last-name.too-long";

    // Other fields
    public static final String USERNAME_REQUIRED = "validation.username.required";
    public static final String USER_ID_REQUIRED = "validation.user-id.required";
    public static final String REFRESH_TOKEN_REQUIRED = "validation.refresh-token.required";
}

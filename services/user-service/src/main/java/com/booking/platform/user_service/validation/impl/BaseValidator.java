package com.booking.platform.user_service.validation.impl;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Base class for validators — provides shared utility methods and localized message resolution.
 */
public abstract class BaseValidator {

    protected final ValidationProperties validationProperties;
    private final MessageSource messageSource;
    private Pattern emailPattern;

    protected BaseValidator(ValidationProperties validationProperties, MessageSource messageSource) {
        this.validationProperties = validationProperties;
        this.messageSource = messageSource;
    }

    /**
     * Resolves a localized message using the authenticated user's preferred language.
     * Falls back to English for unauthenticated requests (no JWT).
     */
    protected String msg(String key, Object... args) {
        String lang = GrpcUserContext.getLocale();
        Locale locale = (lang != null) ? Locale.forLanguageTag(lang) : Locale.ENGLISH;
        return messageSource.getMessage(key, args, locale);
    }

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected void throwIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }
    }

    protected Pattern getEmailPattern() {
        if (emailPattern == null) {
            emailPattern = Pattern.compile(validationProperties.emailPattern());
        }
        return emailPattern;
    }
}

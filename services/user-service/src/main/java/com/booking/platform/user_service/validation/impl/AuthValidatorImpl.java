package com.booking.platform.user_service.validation.impl;

import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.user_service.constants.ValidationMessages;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.AuthValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for authentication-related gRPC requests.
 */
@Component
@EnableConfigurationProperties(ValidationProperties.class)
public class AuthValidatorImpl extends BaseValidator implements AuthValidator {

    public AuthValidatorImpl(ValidationProperties validationProperties, MessageSource messageSource) {
        super(validationProperties, messageSource);
    }

    @Override
    public void validateRegisterRequest(RegisterRequest request) {
        List<String> errors = new ArrayList<>();

        // Email validation
        if (isBlank(request.getEmail())) {
            errors.add(msg(ValidationMessages.EMAIL_REQUIRED));
        } else {
            if (request.getEmail().length() > validationProperties.maxEmailLength()) {
                errors.add(msg(ValidationMessages.EMAIL_TOO_LONG, validationProperties.maxEmailLength()));
            }
            if (!getEmailPattern().matcher(request.getEmail()).matches()) {
                errors.add(msg(ValidationMessages.EMAIL_INVALID_FORMAT));
            }
        }

        // Password validation
        if (isBlank(request.getPassword())) {
            errors.add(msg(ValidationMessages.PASSWORD_REQUIRED));
        } else {
            if (request.getPassword().length() < validationProperties.minPasswordLength()) {
                errors.add(msg(ValidationMessages.PASSWORD_TOO_SHORT, validationProperties.minPasswordLength()));
            }
            if (request.getPassword().length() > validationProperties.maxPasswordLength()) {
                errors.add(msg(ValidationMessages.PASSWORD_TOO_LONG, validationProperties.maxPasswordLength()));
            }
        }

        // Name validation
        if (isBlank(request.getFirstName())) {
            errors.add(msg(ValidationMessages.FIRST_NAME_REQUIRED));
        } else if (request.getFirstName().length() > validationProperties.maxNameLength()) {
            errors.add(msg(ValidationMessages.FIRST_NAME_TOO_LONG, validationProperties.maxNameLength()));
        }

        if (isBlank(request.getLastName())) {
            errors.add(msg(ValidationMessages.LAST_NAME_REQUIRED));
        } else if (request.getLastName().length() > validationProperties.maxNameLength()) {
            errors.add(msg(ValidationMessages.LAST_NAME_TOO_LONG, validationProperties.maxNameLength()));
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateLoginRequest(LoginRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getUsername())) {
            errors.add(msg(ValidationMessages.USERNAME_REQUIRED));
        }

        if (isBlank(request.getPassword())) {
            errors.add(msg(ValidationMessages.PASSWORD_REQUIRED));
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateRefreshToken(String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new ValidationException(msg(ValidationMessages.REFRESH_TOKEN_REQUIRED));
        }
    }
}

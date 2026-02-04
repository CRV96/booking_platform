package com.booking.platform.user_service.validation.impl;

import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.AuthValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for authentication-related gRPC requests.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ValidationProperties.class)
public class AuthValidatorImpl implements AuthValidator {

    private final ValidationProperties validationProperties;
    private Pattern emailPattern;

    @Override
    public void validateRegisterRequest(RegisterRequest request) {
        List<String> errors = new ArrayList<>();

        // Email validation
        if (isBlank(request.getEmail())) {
            errors.add("Email is required");
        } else {
            if (request.getEmail().length() > validationProperties.maxEmailLength()) {
                errors.add("Email must not exceed " + validationProperties.maxEmailLength() + " characters");
            }
            if (!getEmailPattern().matcher(request.getEmail()).matches()) {
                errors.add("Invalid email format");
            }
        }

        // Password validation
        if (isBlank(request.getPassword())) {
            errors.add("Password is required");
        } else {
            if (request.getPassword().length() < validationProperties.minPasswordLength()) {
                errors.add("Password must be at least " + validationProperties.minPasswordLength() + " characters");
            }
            if (request.getPassword().length() > validationProperties.maxPasswordLength()) {
                errors.add("Password must not exceed " + validationProperties.maxPasswordLength() + " characters");
            }
        }

        // Name validation
        if (isBlank(request.getFirstName())) {
            errors.add("First name is required");
        } else if (request.getFirstName().length() > validationProperties.maxNameLength()) {
            errors.add("First name must not exceed " + validationProperties.maxNameLength() + " characters");
        }

        if (isBlank(request.getLastName())) {
            errors.add("Last name is required");
        } else if (request.getLastName().length() > validationProperties.maxNameLength()) {
            errors.add("Last name must not exceed " + validationProperties.maxNameLength() + " characters");
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateLoginRequest(LoginRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getUsername())) {
            errors.add("Username is required");
        }

        if (isBlank(request.getPassword())) {
            errors.add("Password is required");
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateRefreshToken(String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new ValidationException("Refresh token is required");
        }
    }

    private Pattern getEmailPattern() {
        if (emailPattern == null) {
            emailPattern = Pattern.compile(validationProperties.emailPattern());
        }
        return emailPattern;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void throwIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }
    }
}

package com.booking.platform.user_service.validation.impl;

import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.UserValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for user-related gRPC requests.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ValidationProperties.class)
public class UserValidatorImpl implements UserValidator {

    private final ValidationProperties validationProperties;
    private Pattern emailPattern;

    @Override
    public void validateUpdateUserRequest(UpdateUserRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getUserId())) {
            errors.add("User ID is required");
        }

        // Optional field validations
        if (request.hasEmail() && !getEmailPattern().matcher(request.getEmail()).matches()) {
            errors.add("Invalid email format");
        }

        if (request.hasFirstName() && request.getFirstName().length() > validationProperties.maxNameLength()) {
            errors.add("First name must not exceed " + validationProperties.maxNameLength() + " characters");
        }

        if (request.hasLastName() && request.getLastName().length() > validationProperties.maxNameLength()) {
            errors.add("Last name must not exceed " + validationProperties.maxNameLength() + " characters");
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateUserId(String userId) {
        if (isBlank(userId)) {
            throw new ValidationException("User ID is required");
        }
    }

    @Override
    public void validateUsername(String username) {
        if (isBlank(username)) {
            throw new ValidationException("Username is required");
        }
    }

    @Override
    public void validateEmail(String email) {
        if (isBlank(email)) {
            throw new ValidationException("Email is required");
        }
        if (!getEmailPattern().matcher(email).matches()) {
            throw new ValidationException("Invalid email format");
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

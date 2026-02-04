package com.booking.platform.user_service.validation;

import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for user-related gRPC requests.
 */
@Component
public class UserValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 255;

    public void validateRegisterRequest(RegisterRequest request) {
        List<String> errors = new ArrayList<>();

        // Email validation
        if (isBlank(request.getEmail())) {
            errors.add("Email is required");
        } else {
            if (request.getEmail().length() > MAX_EMAIL_LENGTH) {
                errors.add("Email must not exceed " + MAX_EMAIL_LENGTH + " characters");
            }
            if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
                errors.add("Invalid email format");
            }
        }

        // Password validation
        if (isBlank(request.getPassword())) {
            errors.add("Password is required");
        } else {
            if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
                errors.add("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            if (request.getPassword().length() > MAX_PASSWORD_LENGTH) {
                errors.add("Password must not exceed " + MAX_PASSWORD_LENGTH + " characters");
            }
        }

        // Name validation
        if (isBlank(request.getFirstName())) {
            errors.add("First name is required");
        } else if (request.getFirstName().length() > MAX_NAME_LENGTH) {
            errors.add("First name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        if (isBlank(request.getLastName())) {
            errors.add("Last name is required");
        } else if (request.getLastName().length() > MAX_NAME_LENGTH) {
            errors.add("Last name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        throwIfErrors(errors);
    }

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

    public void validateUpdateUserRequest(UpdateUserRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getUserId())) {
            errors.add("User ID is required");
        }

        // Optional field validations
        if (request.hasEmail() && !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            errors.add("Invalid email format");
        }

        if (request.hasFirstName() && request.getFirstName().length() > MAX_NAME_LENGTH) {
            errors.add("First name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        if (request.hasLastName() && request.getLastName().length() > MAX_NAME_LENGTH) {
            errors.add("Last name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        throwIfErrors(errors);
    }

    public void validateUserId(String userId) {
        if (isBlank(userId)) {
            throw new ValidationException("User ID is required");
        }
    }

    public void validateUsername(String username) {
        if (isBlank(username)) {
            throw new ValidationException("Username is required");
        }
    }

    public void validateEmail(String email) {
        if (isBlank(email)) {
            throw new ValidationException("Email is required");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format");
        }
    }

    public void validateRefreshToken(String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new ValidationException("Refresh token is required");
        }
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

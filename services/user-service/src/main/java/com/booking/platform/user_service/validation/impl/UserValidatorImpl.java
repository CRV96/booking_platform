package com.booking.platform.user_service.validation.impl;

import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.constants.ValidationMessages;
import com.booking.platform.user_service.exception.ValidationException;
import com.booking.platform.user_service.properties.ValidationProperties;
import com.booking.platform.user_service.validation.UserValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for user-related gRPC requests.
 */
@Component
@EnableConfigurationProperties(ValidationProperties.class)
public class UserValidatorImpl extends BaseValidator implements UserValidator {

    public UserValidatorImpl(ValidationProperties validationProperties, MessageSource messageSource) {
        super(validationProperties, messageSource);
    }

    @Override
    public void validateUpdateUserRequest(UpdateUserRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getUserId())) {
            errors.add(msg(ValidationMessages.USER_ID_REQUIRED));
        }

        // Optional field validations
        if (request.hasEmail() && !getEmailPattern().matcher(request.getEmail()).matches()) {
            errors.add(msg(ValidationMessages.EMAIL_INVALID_FORMAT));
        }

        if (request.hasFirstName() && request.getFirstName().length() > validationProperties.maxNameLength()) {
            errors.add(msg(ValidationMessages.FIRST_NAME_TOO_LONG, validationProperties.maxNameLength()));
        }

        if (request.hasLastName() && request.getLastName().length() > validationProperties.maxNameLength()) {
            errors.add(msg(ValidationMessages.LAST_NAME_TOO_LONG, validationProperties.maxNameLength()));
        }

        throwIfErrors(errors);
    }

    @Override
    public void validateUserId(String userId) {
        if (isBlank(userId)) {
            throw new ValidationException(msg(ValidationMessages.USER_ID_REQUIRED));
        }
    }

    @Override
    public void validateUsername(String username) {
        if (isBlank(username)) {
            throw new ValidationException(msg(ValidationMessages.USERNAME_REQUIRED));
        }
    }

    @Override
    public void validateEmail(String email) {
        if (isBlank(email)) {
            throw new ValidationException(msg(ValidationMessages.EMAIL_REQUIRED));
        }
        if (!getEmailPattern().matcher(email).matches()) {
            throw new ValidationException(msg(ValidationMessages.EMAIL_INVALID_FORMAT));
        }
    }
}

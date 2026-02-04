package com.booking.platform.user_service.validation;

import com.booking.platform.common.grpc.user.UpdateUserRequest;

/**
 * Validator for user-related operations.
 */
public interface UserValidator {

    void validateUpdateUserRequest(UpdateUserRequest request);

    void validateUserId(String userId);

    void validateUsername(String username);

    void validateEmail(String email);
}

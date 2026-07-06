package com.booking.platform.user_service.exception.user;

import com.booking.platform.user_service.exception.UserServiceException;
import io.grpc.Status;

/**
 * Thrown when a requested user is not found.
 */
public class UserNotFoundException extends UserServiceException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public static UserNotFoundException forId(String userId) {
        return new UserNotFoundException("User not found with ID: " + userId);
    }

    public static UserNotFoundException forUsername(String username) {
        return new UserNotFoundException("User not found with username: " + username);
    }

    public static UserNotFoundException forEmail(String email) {
        return new UserNotFoundException("User not found with email: " + email);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}

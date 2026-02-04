package com.booking.platform.user_service.validation;

import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;

/**
 * Validator for authentication-related operations.
 */
public interface AuthValidator {

    void validateRegisterRequest(RegisterRequest request);

    void validateLoginRequest(LoginRequest request);

    void validateRefreshToken(String refreshToken);
}

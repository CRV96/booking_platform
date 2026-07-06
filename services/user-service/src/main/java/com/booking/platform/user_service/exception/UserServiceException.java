package com.booking.platform.user_service.exception;

import com.booking.platform.common.exception.ServiceException;

/**
 * Base exception for all user service exceptions.
 * Extends the common {@link ServiceException} which provides
 * the gRPC status code mapping used by the shared exception interceptor.
 */
public abstract class UserServiceException extends ServiceException {

    protected UserServiceException(String message) {
        super(message);
    }

    protected UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

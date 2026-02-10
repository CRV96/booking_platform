package com.booking.platform.event_service.exception;

import com.booking.platform.common.exception.ServiceException;

/**
 * Base exception for all event service exceptions.
 * Extends the common {@link ServiceException} which provides
 * the gRPC status code mapping used by the shared exception interceptor.
 */
public abstract class EventServiceException extends ServiceException {

    protected EventServiceException(String message) {
        super(message);
    }

    protected EventServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.booking.platform.booking_service.exception;

import com.booking.platform.common.exception.ServiceException;

/**
 * Base exception for all booking service exceptions.
 * Extends the common {@link ServiceException} which provides
 * the gRPC status code mapping used by the shared
 * {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}.
 */
public abstract class BookingServiceException extends ServiceException {

    protected BookingServiceException(String message) {
        super(message);
    }

    protected BookingServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

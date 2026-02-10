package com.booking.platform.common.exception;

import io.grpc.Status;

/**
 * Base exception for all service-specific exceptions across the platform.
 *
 * Each microservice defines its own exception hierarchy extending this class
 * (e.g., UserServiceException, EventServiceException). The shared
 * {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 * uses {@link #getGrpcStatusCode()} to map exceptions to gRPC status codes.
 *
 * Example:
 * <pre>
 * public class EventNotFoundException extends EventServiceException {
 *     public EventNotFoundException(String id) { super("Event not found: " + id); }
 *     public Status.Code getGrpcStatusCode() { return Status.Code.NOT_FOUND; }
 * }
 * </pre>
 */
public abstract class ServiceException extends RuntimeException {

    protected ServiceException(String message) {
        super(message);
    }

    protected ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the gRPC status code for this exception.
     */
    public abstract Status.Code getGrpcStatusCode();
}

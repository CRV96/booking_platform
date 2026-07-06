package com.booking.platform.common.grpc.interceptor.config;

/**
 * Defines the execution order for gRPC server interceptors.
 *
 * Lower values execute first. The JWT interceptor must run before the
 * exception interceptor so that authentication errors are handled
 * before business logic exceptions.
 */
public final class InterceptorOrder {

    private InterceptorOrder() {}

    /**
     * Correlation ID extraction and propagation (runs before everything).
     */
    public static final int CORRELATION_ID = 1;

    /**
     * JWT validation and context population (runs second).
     */
    public static final int JWT_CONTEXT = 10;

    /**
     * Exception handling and gRPC status mapping (runs second).
     */
    public static final int EXCEPTION_HANDLING = 20;
}

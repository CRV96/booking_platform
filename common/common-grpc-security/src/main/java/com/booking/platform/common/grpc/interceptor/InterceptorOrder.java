package com.booking.platform.common.grpc.interceptor;

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
     * JWT validation and context population (runs first).
     */
    public static final int JWT_CONTEXT = 10;

    /**
     * Exception handling and gRPC status mapping (runs second).
     */
    public static final int EXCEPTION_HANDLING = 20;
}

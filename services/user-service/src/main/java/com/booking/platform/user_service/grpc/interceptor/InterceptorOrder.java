package com.booking.platform.user_service.grpc.interceptor;

/**
 * Defines the execution order for gRPC server interceptors.
 * Lower values execute first (outermost in the interceptor chain).
 *
 * Order: JwtContext (extract user) → Exception handling → Service method
 */
public final class InterceptorOrder {

    private InterceptorOrder() {}

    public static final int JWT_CONTEXT = 10;
    public static final int EXCEPTION_HANDLING = 20;
}

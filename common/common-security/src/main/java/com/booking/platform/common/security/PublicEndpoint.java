package com.booking.platform.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a gRPC service method as publicly accessible without authentication.
 *
 * Methods annotated with this are registered in {@link PublicEndpointRegistry}
 * and the {@link com.booking.platform.common.grpc.interceptor.JwtContextInterceptor}
 * will allow unauthenticated access to them.
 *
 * All other gRPC methods are secure-by-default (JWT required).
 *
 * Usage:
 * <pre>
 * {@literal @}PublicEndpoint
 * public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
 *     // No JWT needed
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}

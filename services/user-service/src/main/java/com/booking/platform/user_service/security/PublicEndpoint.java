package com.booking.platform.user_service.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a gRPC service method as publicly accessible without authentication.
 *
 * Methods annotated with {@code @PublicEndpoint} will not require a valid JWT token.
 * All other gRPC methods require authentication by default.
 *
 * Usage:
 * <pre>
 * {@code
 * @PublicEndpoint
 * @Override
 * public void login(LoginRequest request, StreamObserver<AuthResponse> response) {
 *     // This method can be called without a JWT token
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}

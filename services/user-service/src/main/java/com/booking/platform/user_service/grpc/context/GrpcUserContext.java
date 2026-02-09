package com.booking.platform.user_service.grpc.context;

import io.grpc.Context;

import java.time.Instant;
import java.util.List;

/**
 * Holds gRPC Context keys for authenticated user claims.
 * Populated by {@link com.booking.platform.user_service.grpc.interceptor.JwtContextInterceptor}
 * from the JWT token forwarded by the gateway.
 *
 * Usage in gRPC service methods:
 * <pre>
 *     String userId = GrpcUserContext.getUserId();
 *     String username = GrpcUserContext.getUsername();
 * </pre>
 */
public final class GrpcUserContext {

    private GrpcUserContext() {}

    public static final Context.Key<String> USER_ID = Context.key("jwt-user-id");
    public static final Context.Key<String> USERNAME = Context.key("jwt-username");
    public static final Context.Key<String> EMAIL = Context.key("jwt-email");
    public static final Context.Key<List<String>> ROLES = Context.key("jwt-roles");
    public static final Context.Key<String> JWT_TOKEN = Context.key("jwt-token");
    public static final Context.Key<String> JWT_ID = Context.key("jwt-id");
    public static final Context.Key<Instant> JWT_EXPIRY = Context.key("jwt-expiry");

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static String getEmail() {
        return EMAIL.get();
    }

    public static List<String> getRoles() {
        List<String> roles = ROLES.get();
        return roles != null ? roles : List.of();
    }

    public static String getJwtToken() {
        return JWT_TOKEN.get();
    }

    public static String getJwtId() {
        return JWT_ID.get();
    }

    public static Instant getJwtExpiry() {
        return JWT_EXPIRY.get();
    }

    public static boolean isAuthenticated() {
        return USER_ID.get() != null;
    }
}

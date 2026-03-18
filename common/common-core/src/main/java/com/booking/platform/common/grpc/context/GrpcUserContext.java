package com.booking.platform.common.grpc.context;

import io.grpc.Context;

import java.time.Instant;
import java.util.List;

/**
 * Holds gRPC Context keys for authenticated user claims.
 * Populated by {@link com.booking.platform.common.grpc.interceptor.JwtContextInterceptor}
 * from the JWT token forwarded by the gateway.
 *
 * Usage in gRPC service methods:
 * <pre>
 *     String userId = GrpcUserContext.getUserId();
 *     List&lt;String&gt; roles = GrpcUserContext.getRoles();
 *     boolean isEmployee = GrpcUserContext.hasRole("employee");
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
    public static final Context.Key<String> LOCALE = Context.key("jwt-locale");

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

    public static String getLocale() {
        return LOCALE.get();
    }

    public static boolean isAuthenticated() {
        return USER_ID.get() != null;
    }

    /**
     * Checks if the authenticated user has the specified role.
     *
     * @param role the role to check (e.g., "employee", "customer")
     * @return true if the user has the role
     */
    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }
}

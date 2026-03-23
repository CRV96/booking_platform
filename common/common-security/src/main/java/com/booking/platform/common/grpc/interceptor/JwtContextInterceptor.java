package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.interceptor.config.InterceptorOrder;
import com.booking.platform.common.security.JwtValidatorService;
import com.booking.platform.common.security.PublicEndpointRegistry;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;
import java.util.Map;

/**
 * gRPC server interceptor that validates JWT tokens and enforces authentication.
 *
 * Authentication behavior:
 * - Methods annotated with {@code @PublicEndpoint}: No JWT required
 * - All other methods: Valid JWT required, or UNAUTHENTICATED is returned
 *
 * When a valid JWT is present, this interceptor:
 * - Validates signature (via Keycloak's JWKS public keys)
 * - Validates expiry (exp claim)
 * - Validates issuer (iss claim)
 * - Checks Redis blacklist (revoked tokens)
 * - Populates {@link GrpcUserContext} with user claims
 */
@Slf4j
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true")
@Order(InterceptorOrder.JWT_CONTEXT)
public class JwtContextInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtValidatorService jwtValidator;
    private final PublicEndpointRegistry publicEndpointRegistry;

    public JwtContextInterceptor(JwtValidatorService jwtValidator, PublicEndpointRegistry publicEndpointRegistry) {
        this.jwtValidator = jwtValidator;
        this.publicEndpointRegistry = publicEndpointRegistry;
        log.info("JwtContextInterceptor initialized — gRPC JWT authentication is ACTIVE");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getBareMethodName();
        boolean isPublic = publicEndpointRegistry.isPublicEndpoint(methodName);

        String authHeader = headers.get(AUTHORIZATION_KEY);
        boolean hasToken = authHeader != null && authHeader.startsWith("Bearer ");

        // Case 1: Has token - validate and set context
        if (hasToken) {
            String token = authHeader.substring(7);

            try {
                Jwt jwt = jwtValidator.validateAndDecode(token);

                String userId = jwt.getSubject();
                String username = jwt.getClaimAsString("preferred_username");
                String email = jwt.getClaimAsString("email");
                String locale = jwt.getClaimAsString("preferred_language");
                List<String> roles = extractRoles(jwt);

                Context context = Context.current()
                        .withValue(GrpcUserContext.USER_ID, userId)
                        .withValue(GrpcUserContext.USERNAME, username)
                        .withValue(GrpcUserContext.EMAIL, email)
                        .withValue(GrpcUserContext.LOCALE, locale)
                        .withValue(GrpcUserContext.ROLES, roles)
                        .withValue(GrpcUserContext.JWT_TOKEN, token)
                        .withValue(GrpcUserContext.JWT_ID, jwt.getId())
                        .withValue(GrpcUserContext.JWT_EXPIRY, jwt.getExpiresAt());

                log.debug("JWT validated for user: {} ({}) on method: {}", username, userId, methodName);
                return Contexts.interceptCall(context, call, headers, next);

            } catch (JwtException e) {
                log.warn("JWT validation failed for method {}: {}", methodName, e.getMessage());
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        // Case 2: No token, but public endpoint - allow
        if (isPublic) {
            log.debug("Public endpoint accessed without token: {}", methodName);
            return next.startCall(call, headers);
        }

        // Case 3: No token, private endpoint - reject
        log.warn("Authentication required for method: {}", methodName);
        call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), new Metadata());
        return new ServerCall.Listener<>() {};
    }

    /**
     * Extracts realm roles from the {@code realm_access.roles} claim.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof List<?>) {
                return (List<String>) roles;
            }
        }
        return List.of();
    }
}

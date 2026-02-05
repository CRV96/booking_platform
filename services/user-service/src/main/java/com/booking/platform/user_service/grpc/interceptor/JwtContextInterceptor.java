package com.booking.platform.user_service.grpc.interceptor;

import com.booking.platform.user_service.grpc.context.GrpcUserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * gRPC server interceptor that extracts JWT claims from incoming metadata
 * and populates the gRPC Context with user information.
 *
 * The JWT has already been validated by the gateway (signature, expiry, issuer).
 * This interceptor only decodes the payload to extract claims — no re-validation.
 *
 * If no token is present (public endpoints like login/register), the call
 * continues without setting user context.
 */
@Slf4j
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true")
@Order(InterceptorOrder.JWT_CONTEXT)
public class JwtContextInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String authHeader = headers.get(AUTHORIZATION_KEY);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                Map<String, Object> claims = decodeJwtPayload(token);

                String userId = (String) claims.get("sub");
                String username = (String) claims.get("preferred_username");
                String email = (String) claims.get("email");
                List<String> roles = extractRoles(claims);

                Context context = Context.current()
                        .withValue(GrpcUserContext.USER_ID, userId)
                        .withValue(GrpcUserContext.USERNAME, username)
                        .withValue(GrpcUserContext.EMAIL, email)
                        .withValue(GrpcUserContext.ROLES, roles)
                        .withValue(GrpcUserContext.JWT_TOKEN, token);

                log.debug("JWT context set for user: {} ({})", username, userId);
                return Contexts.interceptCall(context, call, headers, next);

            } catch (Exception e) {
                log.warn("Failed to decode JWT payload: {}", e.getMessage());
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        // No token — continue without user context (public endpoints)
        return next.startCall(call, headers);
    }

    /**
     * Decodes the JWT payload (second segment) from base64 without signature verification.
     * The gateway has already validated the token.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJwtPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return OBJECT_MAPPER.readValue(payload, new TypeReference<>() {});
    }

    /**
     * Extracts realm roles from the {@code realm_access.roles} claim.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roles = realmAccessMap.get("roles");
            if (roles instanceof List<?>) {
                return (List<String>) roles;
            }
        }
        return List.of();
    }
}

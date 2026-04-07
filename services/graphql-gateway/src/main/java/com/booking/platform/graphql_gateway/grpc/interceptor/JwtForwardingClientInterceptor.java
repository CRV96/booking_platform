package com.booking.platform.graphql_gateway.grpc.interceptor;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * gRPC client interceptor that forwards the JWT token from Spring SecurityContext
 * into gRPC metadata for service-to-service propagation.
 *
 * On each outgoing gRPC call, if a JWT is present in the SecurityContextHolder,
 * it is added as a Bearer token in the {@code authorization} metadata key.
 * If no authentication is present (anonymous/public requests), the call proceeds without a token.
 */
@Slf4j
@GrpcGlobalClientInterceptor
public class JwtForwardingClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of(GatewayConstants.Security.GRPC_AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                    String tokenValue = jwtAuth.getToken().getTokenValue();
                    headers.put(AUTHORIZATION_KEY, GatewayConstants.Security.BEARER_PREFIX + tokenValue);
                    log.debug("Forwarding JWT to gRPC call: {}", method.getFullMethodName());
                }

                super.start(responseListener, headers);
            }
        };
    }
}

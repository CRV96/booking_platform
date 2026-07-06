package com.booking.platform.graphql_gateway.grpc.interceptor;

import com.booking.platform.graphql_gateway.constants.GatewayConstants;
import io.grpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtForwardingClientInterceptorTest {

    @Mock private Channel channel;
    @Mock private ClientCall<Object, Object> clientCall;
    @Mock private ClientCall.Listener<Object> listener;

    private final JwtForwardingClientInterceptor interceptor = new JwtForwardingClientInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ClientCall<Object, Object> intercept() {
        MethodDescriptor<Object, Object> method = mock(MethodDescriptor.class);
        when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
                .thenReturn((ClientCall) clientCall);
        return interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
    }

    private void setJwt(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    // ── JWT present ───────────────────────────────────────────────────────────

    @Test
    void interceptCall_jwtPresent_injectsAuthorizationHeader() {
        setJwt("my-jwt-token");

        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(eq(listener), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of(GatewayConstants.Security.GRPC_AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headersCaptor.getValue().get(authKey)).isEqualTo("Bearer my-jwt-token");
    }

    @Test
    void interceptCall_jwtPresent_prefixesBearerScheme() {
        setJwt("abc.def.ghi");

        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of(GatewayConstants.Security.GRPC_AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER);
        String header = headersCaptor.getValue().get(authKey);
        assertThat(header).startsWith(GatewayConstants.Security.BEARER_PREFIX);
        assertThat(header).endsWith("abc.def.ghi");
    }

    // ── No JWT — anonymous request ────────────────────────────────────────────

    @Test
    void interceptCall_noAuthentication_doesNotAddAuthorizationHeader() {
        // No JWT in SecurityContext (anonymous)
        ClientCall<Object, Object> wrapped = intercept();
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(eq(listener), headersCaptor.capture());
        Metadata.Key<String> authKey =
                Metadata.Key.of(GatewayConstants.Security.GRPC_AUTHORIZATION_HEADER, Metadata.ASCII_STRING_MARSHALLER);
        assertThat(headersCaptor.getValue().get(authKey)).isNull();
    }

    @Test
    void interceptCall_noAuthentication_stillForwardsCallToNextChannel() {
        ClientCall<Object, Object> wrapped = intercept();
        wrapped.start(listener, new Metadata());

        verify(clientCall).start(any(), any());
    }
}

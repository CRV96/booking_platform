package com.booking.platform.common.grpc.interceptor;

import com.booking.platform.common.security.JwtValidatorService;
import com.booking.platform.common.security.PublicEndpointRegistry;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtContextInterceptorTest {

    @Mock private JwtValidatorService jwtValidator;
    @Mock private PublicEndpointRegistry publicEndpointRegistry;
    @Mock private ServerCall<Object, Object> call;
    @Mock private ServerCallHandler<Object, Object> handler;
    @Mock private ServerCall.Listener<Object> listener;

    private JwtContextInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtContextInterceptor(jwtValidator, publicEndpointRegistry);
        when(handler.startCall(any(), any())).thenReturn(listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MethodDescriptor<Object, Object> method(String name) {
        MethodDescriptor m = mock(MethodDescriptor.class);
        when(m.getBareMethodName()).thenReturn(name);
        return m;
    }

    private Metadata headersWithToken(String token) {
        Metadata h = new Metadata();
        h.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return h;
    }

    private Jwt validJwt() {
        return Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("preferred_username", "alice")
                .claim("email", "a@b.com")
                .claim("realm_access", Map.of("roles", List.of("customer")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    // ── valid token ───────────────────────────────────────────────────────────

    @Test
    void validToken_privateEndpoint_proceedsWithoutClosing() {
        MethodDescriptor<Object, Object> m = method("getEvent");
        when(jwtValidator.validateAndDecode("tok")).thenReturn(validJwt());
        when(publicEndpointRegistry.isPublicEndpoint(anyString())).thenReturn(false);
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, headersWithToken("tok"), handler);

        verify(call, never()).close(any(), any());
    }

    @Test
    void validToken_populatesContext_callProceeds() {
        MethodDescriptor<Object, Object> m = method("createBooking");
        when(jwtValidator.validateAndDecode("tok")).thenReturn(validJwt());
        when(publicEndpointRegistry.isPublicEndpoint(anyString())).thenReturn(false);
        when(call.getMethodDescriptor()).thenReturn(m);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, headersWithToken("tok"), handler);

        assertThat(result).isNotNull();
    }

    // ── invalid token ─────────────────────────────────────────────────────────

    @Test
    void invalidToken_closesCallWithUnauthenticated() {
        MethodDescriptor<Object, Object> m = method("getBooking");
        when(jwtValidator.validateAndDecode(anyString())).thenThrow(new JwtException("expired"));
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, headersWithToken("bad-tok"), handler);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void invalidToken_doesNotProceedToHandler() {
        MethodDescriptor<Object, Object> m = method("getBooking");
        when(jwtValidator.validateAndDecode(anyString())).thenThrow(new JwtException("invalid sig"));
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, headersWithToken("bad"), handler);

        verify(handler, never()).startCall(any(), any());
    }

    // ── no token, public endpoint ─────────────────────────────────────────────

    @Test
    void noToken_publicEndpoint_proceedsToHandler() {
        MethodDescriptor<Object, Object> m = method("register");
        when(publicEndpointRegistry.isPublicEndpoint("register")).thenReturn(true);
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, new Metadata(), handler);

        verify(handler).startCall(any(), any());
        verify(call, never()).close(any(), any());
    }

    // ── no token, private endpoint ────────────────────────────────────────────

    @Test
    void noToken_privateEndpoint_closesWithUnauthenticated() {
        MethodDescriptor<Object, Object> m = method("getUser");
        when(publicEndpointRegistry.isPublicEndpoint("getUser")).thenReturn(false);
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, new Metadata(), handler);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void noToken_privateEndpoint_doesNotProceedToHandler() {
        MethodDescriptor<Object, Object> m = method("searchUsers");
        when(publicEndpointRegistry.isPublicEndpoint("searchUsers")).thenReturn(false);
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, new Metadata(), handler);

        verify(handler, never()).startCall(any(), any());
    }

    // ── role extraction ───────────────────────────────────────────────────────

    @Test
    void validToken_nullRealmAccess_stillProceeds() {
        MethodDescriptor<Object, Object> m = method("getUser");
        Jwt jwtNoRoles = Jwt.withTokenValue("tok2")
                .header("alg", "RS256")
                .subject("user-2")
                .claim("preferred_username", "bob")
                .claim("email", "b@c.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtValidator.validateAndDecode("tok2")).thenReturn(jwtNoRoles);
        when(publicEndpointRegistry.isPublicEndpoint(anyString())).thenReturn(false);
        when(call.getMethodDescriptor()).thenReturn(m);

        interceptor.interceptCall(call, headersWithToken("tok2"), handler);

        verify(call, never()).close(any(), any());
    }
}

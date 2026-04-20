package com.booking.platform.graphql_gateway.graphql.resolver;

import com.booking.platform.common.grpc.user.AuthResponse;
import com.booking.platform.graphql_gateway.dto.auth.AuthPayload;
import com.booking.platform.graphql_gateway.dto.auth.LoginInput;
import com.booking.platform.graphql_gateway.dto.auth.RegisterInput;
import com.booking.platform.graphql_gateway.grpc.client.AuthClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthResolverTest {

    @Mock private AuthClient authClient;

    @InjectMocks private AuthResolver resolver;

    private static final AuthResponse AUTH_RESPONSE = AuthResponse.newBuilder()
            .setAccessToken("access-tok")
            .setRefreshToken("refresh-tok")
            .setExpiresIn(300)
            .setRefreshExpiresIn(1800)
            .setTokenType("Bearer")
            .build();

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_delegatesToAuthClient() {
        RegisterInput input = new RegisterInput(
                "alice@test.com", "pass", "Alice", "Smith",
                "+1234567890", "US", "en");
        when(authClient.register(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(AUTH_RESPONSE);

        resolver.register(input);

        verify(authClient).register("alice@test.com", "pass", "Alice", "Smith",
                "+1234567890", "US", "en");
    }

    @Test
    void register_mapsGrpcResponseToAuthPayload() {
        RegisterInput input = new RegisterInput(
                "bob@test.com", "secret", "Bob", "Jones",
                "+44000", "GB", "en");
        when(authClient.register(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AUTH_RESPONSE);

        AuthPayload payload = resolver.register(input);

        assertThat(payload.accessToken()).isEqualTo("access-tok");
        assertThat(payload.refreshToken()).isEqualTo("refresh-tok");
        assertThat(payload.tokenType()).isEqualTo("Bearer");
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_delegatesToAuthClient() {
        LoginInput input = new LoginInput("carol", "pw123");
        when(authClient.login("carol", "pw123")).thenReturn(AUTH_RESPONSE);

        resolver.login(input);

        verify(authClient).login("carol", "pw123");
    }

    @Test
    void login_returnsAuthPayload() {
        LoginInput input = new LoginInput("dave", "pw");
        when(authClient.login(any(), any())).thenReturn(AUTH_RESPONSE);

        AuthPayload payload = resolver.login(input);

        assertThat(payload.accessToken()).isEqualTo("access-tok");
        assertThat(payload.expiresIn()).isEqualTo(300);
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_delegatesToAuthClient() {
        when(authClient.refreshToken("refresh-123")).thenReturn(AUTH_RESPONSE);

        resolver.refreshToken("refresh-123");

        verify(authClient).refreshToken("refresh-123");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_delegatesToAuthClient() {
        when(authClient.logout("refresh-tok")).thenReturn(true);

        resolver.logout("refresh-tok");

        verify(authClient).logout("refresh-tok");
    }

    @Test
    void logout_successReturnsTrue() {
        when(authClient.logout(any())).thenReturn(true);

        assertThat(resolver.logout("tok").success()).isTrue();
    }

    @Test
    void logout_failureReturnsFalse() {
        when(authClient.logout(any())).thenReturn(false);

        assertThat(resolver.logout("tok").success()).isFalse();
    }
}

package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceAuthClientImplTest {

    @Mock private AuthServiceGrpc.AuthServiceBlockingStub stub;

    private UserServiceAuthClientImpl client;

    private final AuthResponse defaultAuthResponse = AuthResponse.getDefaultInstance();

    @BeforeEach
    void setUp() {
        client = new UserServiceAuthClientImpl();
        ReflectionTestUtils.setField(client, "authServiceStub", stub);
        when(stub.register(any())).thenReturn(defaultAuthResponse);
        when(stub.login(any())).thenReturn(defaultAuthResponse);
        when(stub.refreshToken(any())).thenReturn(defaultAuthResponse);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_allFieldsNonNull_setsAllOnRequest() {
        client.register("a@b.com", "pass", "Alice", "Smith", "+1234", "US", "en");

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(stub).register(captor.capture());
        RegisterRequest req = captor.getValue();
        assertThat(req.getEmail()).isEqualTo("a@b.com");
        assertThat(req.getPassword()).isEqualTo("pass");
        assertThat(req.getFirstName()).isEqualTo("Alice");
        assertThat(req.getLastName()).isEqualTo("Smith");
        assertThat(req.getPhoneNumber()).isEqualTo("+1234");
        assertThat(req.getCountry()).isEqualTo("US");
        assertThat(req.getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    void register_optionalFieldsNull_notSetOnRequest() {
        client.register("a@b.com", "pass", "Alice", "Smith", null, null, null);

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(stub).register(captor.capture());
        RegisterRequest req = captor.getValue();
        assertThat(req.hasPhoneNumber()).isFalse();
        assertThat(req.hasCountry()).isFalse();
        assertThat(req.hasPreferredLanguage()).isFalse();
    }

    @Test
    void register_returnsAuthResponse() {
        AuthResponse result = client.register("a@b.com", "pass", "Alice", "Smith", null, null, null);
        assertThat(result).isEqualTo(defaultAuthResponse);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_sendsUsernameAndPassword() {
        client.login("alice", "secret");

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(stub).login(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPassword()).isEqualTo("secret");
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_sendsRefreshToken() {
        client.refreshToken("ref-tok");

        ArgumentCaptor<RefreshTokenRequest> captor = ArgumentCaptor.forClass(RefreshTokenRequest.class);
        verify(stub).refreshToken(captor.capture());
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("ref-tok");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_returnsTrue_whenServiceReturnsTrue() {
        when(stub.logout(any())).thenReturn(LogoutResponse.newBuilder().setSuccess(true).build());

        boolean result = client.logout("ref-tok");

        assertThat(result).isTrue();
    }

    @Test
    void logout_returnsFalse_whenServiceReturnsFalse() {
        when(stub.logout(any())).thenReturn(LogoutResponse.newBuilder().setSuccess(false).build());

        boolean result = client.logout("ref-tok");

        assertThat(result).isFalse();
    }

    @Test
    void logout_sendsRefreshToken() {
        when(stub.logout(any())).thenReturn(LogoutResponse.newBuilder().setSuccess(true).build());
        client.logout("my-refresh-token");

        ArgumentCaptor<LogoutRequest> captor = ArgumentCaptor.forClass(LogoutRequest.class);
        verify(stub).logout(captor.capture());
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("my-refresh-token");
    }
}

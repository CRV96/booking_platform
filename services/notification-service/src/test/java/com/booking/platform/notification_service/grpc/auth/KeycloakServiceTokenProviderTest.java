package com.booking.platform.notification_service.grpc.auth;

import com.booking.platform.notification_service.properties.KeycloakServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakServiceTokenProviderTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec postSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private final KeycloakServiceProperties properties =
            new KeycloakServiceProperties("http://keycloak/token", "svc-client", "secret");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeycloakServiceTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new KeycloakServiceTokenProvider(restClient, properties, objectMapper);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    private String tokenResponse(String token, int expiresIn) {
        return String.format("{\"access_token\":\"%s\",\"expires_in\":%d}", token, expiresIn);
    }

    // ── Cache miss — no cached token ──────────────────────────────────────────

    @Test
    void getToken_noCachedToken_callsKeycloak() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("tok-abc", 300));

        String token = tokenProvider.getToken();

        assertThat(token).isEqualTo("tok-abc");
        verify(restClient).post();
    }

    @Test
    void getToken_noCachedToken_setsTokenFromResponse() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("fresh-token", 600));

        String token = tokenProvider.getToken();

        assertThat(token).isEqualTo("fresh-token");
    }

    @Test
    void getToken_usesConfiguredTokenUrl() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("t", 300));

        tokenProvider.getToken();

        verify(postSpec).uri("http://keycloak/token");
    }

    @Test
    void getToken_includesClientCredentialsInBody() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("t", 300));

        tokenProvider.getToken();

        verify(bodySpec).body(
                argThat((String body) -> body.contains("grant_type=client_credentials")
                        && body.contains("client_id=svc-client")
                        && body.contains("client_secret=secret")));
    }

    // ── Cache hit — fresh token ───────────────────────────────────────────────

    @Test
    void getToken_cachedAndNotExpired_returnsCachedWithoutCallingKeycloak() {
        // Pre-seed cache with a fresh token
        ReflectionTestUtils.setField(tokenProvider, "cachedToken", "cached-token");
        ReflectionTestUtils.setField(tokenProvider, "tokenExpiry", Instant.now().plusSeconds(300));

        String token = tokenProvider.getToken();

        assertThat(token).isEqualTo("cached-token");
        verifyNoInteractions(restClient);
    }

    @Test
    void getToken_calledTwice_secondCallReturnsCachedToken() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("tok-1", 300));

        tokenProvider.getToken(); // populates cache
        String second = tokenProvider.getToken();

        assertThat(second).isEqualTo("tok-1");
        verify(restClient, times(1)).post(); // only one Keycloak call
    }

    // ── Cache miss — expired token ────────────────────────────────────────────

    @Test
    void getToken_expiredToken_refreshesFromKeycloak() {
        // Pre-seed with expired token
        ReflectionTestUtils.setField(tokenProvider, "cachedToken", "old-token");
        ReflectionTestUtils.setField(tokenProvider, "tokenExpiry", Instant.now().minusSeconds(60));
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("new-token", 300));

        String token = tokenProvider.getToken();

        assertThat(token).isEqualTo("new-token");
        verify(restClient).post();
    }

    // ── Expiry calculation ────────────────────────────────────────────────────

    @Test
    void getToken_expirySetWithBuffer() {
        when(responseSpec.body(String.class)).thenReturn(tokenResponse("t", 300));

        Instant before = Instant.now();
        tokenProvider.getToken();
        Instant after = Instant.now();

        Instant expiry = (Instant) ReflectionTestUtils.getField(tokenProvider, "tokenExpiry");
        // expiry = Instant.now() + expiresIn(300) - buffer(30) = now + 270s
        Instant expectedMin = before.plusSeconds(270 - 2);
        Instant expectedMax = after.plusSeconds(270 + 2);
        assertThat(expiry).isBetween(expectedMin, expectedMax);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void getToken_malformedResponse_throwsRuntimeException() {
        when(responseSpec.body(String.class)).thenReturn("not-json");

        assertThatThrownBy(() -> tokenProvider.getToken())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse Keycloak token response");
    }

    @Test
    void getToken_missingAccessTokenField_throwsRuntimeException() {
        when(responseSpec.body(String.class)).thenReturn("{\"expires_in\":300}");

        assertThatThrownBy(() -> tokenProvider.getToken())
                .isInstanceOf(RuntimeException.class);
    }
}

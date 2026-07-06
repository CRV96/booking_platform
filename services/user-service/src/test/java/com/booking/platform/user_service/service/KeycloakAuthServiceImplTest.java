package com.booking.platform.user_service.service;

import com.booking.platform.user_service.dto.TokenResponseDTO;
import com.booking.platform.user_service.exception.auth.AuthenticationException;
import com.booking.platform.user_service.exception.auth.InvalidCredentialsException;
import com.booking.platform.user_service.exception.auth.InvalidTokenException;
import com.booking.platform.user_service.properties.KeycloakAuthProperties;
import com.booking.platform.user_service.service.impl.KeycloakAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAuthServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WebClient webClient;

    private final KeycloakAuthProperties authProps =
            new KeycloakAuthProperties("http://keycloak:8080", "booking-platform", "booking-app");

    private KeycloakAuthServiceImpl service;

    private final TokenResponseDTO tokenResponse =
            new TokenResponseDTO("access-tok", "refresh-tok", 300, 1800, "Bearer");

    @BeforeEach
    void setUp() {
        service = new KeycloakAuthServiceImpl(webClient, authProps);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsTokenResponse() {
        stubBodyToMono(Mono.just(tokenResponse));

        TokenResponseDTO result = service.login("john", "pass");

        assertThat(result.accessToken()).isEqualTo("access-tok");
        assertThat(result.refreshToken()).isEqualTo("refresh-tok");
    }

    @Test
    void login_401Unauthorized_throwsInvalidCredentialsException() {
        stubBodyToMono(Mono.error(create401()));

        assertThatThrownBy(() -> service.login("john", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_400BadRequest_throwsInvalidCredentialsException() {
        stubBodyToMono(Mono.error(create400()));

        assertThatThrownBy(() -> service.login("john", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_otherError_throwsAuthenticationException() {
        stubBodyToMono(Mono.error(new RuntimeException("connection refused")));

        assertThatThrownBy(() -> service.login("john", "pass"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Authentication failed");
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_success_returnsNewTokens() {
        stubBodyToMono(Mono.just(tokenResponse));

        TokenResponseDTO result = service.refreshToken("old-refresh");

        assertThat(result.accessToken()).isEqualTo("access-tok");
    }

    @Test
    void refreshToken_400BadRequest_throwsInvalidTokenException() {
        stubBodyToMono(Mono.error(create400()));

        assertThatThrownBy(() -> service.refreshToken("bad-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshToken_otherError_throwsAuthenticationException() {
        stubBodyToMono(Mono.error(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.refreshToken("token"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Token refresh failed");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_success_returnsTrue() {
        when(webClient.post().uri(anyString()).contentType(any()).body(any())
                .retrieve().toBodilessEntity())
                .thenReturn(Mono.just(ResponseEntity.ok().build()));

        assertThat(service.logout("refresh-token")).isTrue();
    }

    @Test
    void logout_failure_returnsFalse() {
        when(webClient.post().uri(anyString()).contentType(any()).body(any())
                .retrieve().toBodilessEntity())
                .thenReturn(Mono.error(new RuntimeException("already invalidated")));

        assertThat(service.logout("expired-token")).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubBodyToMono(Mono<TokenResponseDTO> mono) {
        when(webClient.post().uri(anyString()).contentType(any()).body(any())
                .retrieve().bodyToMono(any(Class.class)))
                .thenReturn((Mono) mono);
    }

    private static WebClientResponseException create401() {
        return WebClientResponseException.create(401, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    private static WebClientResponseException create400() {
        return WebClientResponseException.create(400, "Bad Request",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}

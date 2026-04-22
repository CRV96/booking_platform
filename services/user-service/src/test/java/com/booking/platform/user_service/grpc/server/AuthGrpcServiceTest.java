package com.booking.platform.user_service.grpc.server;

import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.user.*;
import com.booking.platform.common.security.TokenBlacklistService;
import com.booking.platform.user_service.dto.TokenResponseDTO;
import com.booking.platform.user_service.mapper.AttributeMapper;
import com.booking.platform.user_service.mapper.UserGrpcMapper;
import com.booking.platform.user_service.service.AuthService;
import com.booking.platform.user_service.service.KeycloakUserService;
import com.booking.platform.user_service.validation.AuthValidator;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthGrpcServiceTest {

    @Mock private AuthService authService;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private UserGrpcMapper userGrpcMapper;
    @Mock private AttributeMapper attributeMapper;
    @Mock private AuthValidator authValidator;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @Mock private StreamObserver<AuthResponse> responseObserver;
    @Mock private StreamObserver<LogoutResponse> logoutObserver;

    private AuthGrpcService service;

    // gRPC context management for logout tests
    private Context.CancellableContext grpcCtx;
    private Context previousContext;

    private static final TokenResponseDTO TOKENS =
            new TokenResponseDTO("access-tok", "refresh-tok", 300, 1800, "Bearer");

    @BeforeEach
    void setUp() {
        service = new AuthGrpcService(authService, keycloakUserService, userGrpcMapper, attributeMapper,
                authValidator, tokenBlacklistService);

        // Attach a default gRPC context with user/token info (used by logout tests)
        grpcCtx = Context.current()
                .withValue(GrpcUserContext.USER_ID, "u-1")
                .withValue(GrpcUserContext.JWT_ID, "jti-123")
                .withValue(GrpcUserContext.JWT_EXPIRY, Instant.now().plusSeconds(300))
                .withCancellation();
        previousContext = grpcCtx.attach();

        // Common stubs
        when(userGrpcMapper.toUserInfo(any(), any())).thenReturn(UserInfo.getDefaultInstance());
    }

    @AfterEach
    void tearDown() {
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_createsUserAndAutoLogins() {
        UserRepresentation user = makeUser("new-user", "alice@example.com");
        when(attributeMapper.fromRegisterRequest(any())).thenReturn(Map.of());
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("new-user");
        when(authService.login("alice@example.com", "P@ssw0rd")).thenReturn(TOKENS);
        when(keycloakUserService.getUserById("new-user")).thenReturn(user);
        when(keycloakUserService.getUserRoles("new-user")).thenReturn(List.of("customer"));

        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("alice@example.com")
                .setPassword("P@ssw0rd")
                .setFirstName("Alice")
                .setLastName("Smith")
                .build();
        service.register(request, responseObserver);

        verify(authValidator).validateRegisterRequest(request);
        verify(attributeMapper).fromRegisterRequest(request);
        verify(keycloakUserService).createUser("alice@example.com", "P@ssw0rd", "Alice", "Smith", Map.of());
        verify(authService).login("alice@example.com", "P@ssw0rd");
        verify(keycloakUserService).getUserById("new-user");
        verify(keycloakUserService).getUserRoles("new-user");
        verify(keycloakUserService).sendVerificationEmail("new-user");
        verify(userGrpcMapper).toUserInfo(eq(user), eq(List.of("customer")));
        verify(responseObserver).onNext(any(AuthResponse.class));
        verify(responseObserver).onCompleted();
    }

    @Test
    void register_responseContainsTokenFields() {
        UserRepresentation user = makeUser("new-user", "alice@example.com");
        when(attributeMapper.fromRegisterRequest(any())).thenReturn(Map.of());
        when(keycloakUserService.createUser(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("new-user");
        when(authService.login(anyString(), anyString())).thenReturn(TOKENS);
        when(keycloakUserService.getUserById("new-user")).thenReturn(user);
        when(keycloakUserService.getUserRoles("new-user")).thenReturn(List.of());

        RegisterRequest request = RegisterRequest.newBuilder()
                .setEmail("alice@example.com")
                .setPassword("P@ssw0rd")
                .setFirstName("Alice")
                .setLastName("Smith")
                .build();
        service.register(request, responseObserver);

        verify(responseObserver).onNext(argThat(resp ->
                resp.getAccessToken().equals("access-tok") &&
                resp.getRefreshToken().equals("refresh-tok") &&
                resp.getExpiresIn() == 300 &&
                resp.getRefreshExpiresIn() == 1800 &&
                resp.getTokenType().equals("Bearer")
        ));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validatesAndReturnsAuthResponse() {
        UserRepresentation user = makeUser("user-1", "john");
        when(authService.login("john", "secret")).thenReturn(TOKENS);
        when(keycloakUserService.getUserByUsername("john")).thenReturn(user);
        when(keycloakUserService.getUserRoles("user-1")).thenReturn(List.of("customer"));

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("john")
                .setPassword("secret")
                .build();
        service.login(request, responseObserver);

        verify(authValidator).validateLoginRequest(request);
        verify(authService).login("john", "secret");
        verify(keycloakUserService).getUserByUsername("john");
        verify(keycloakUserService).getUserRoles("user-1");
        verify(userGrpcMapper).toUserInfo(eq(user), eq(List.of("customer")));
        verify(responseObserver).onNext(any(AuthResponse.class));
        verify(responseObserver).onCompleted();
    }

    @Test
    void login_responseContainsTokenFields() {
        UserRepresentation user = makeUser("user-1", "john");
        when(authService.login("john", "secret")).thenReturn(TOKENS);
        when(keycloakUserService.getUserByUsername("john")).thenReturn(user);
        when(keycloakUserService.getUserRoles("user-1")).thenReturn(List.of());

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("john")
                .setPassword("secret")
                .build();
        service.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(resp ->
                resp.getAccessToken().equals("access-tok") &&
                resp.getRefreshToken().equals("refresh-tok") &&
                resp.getTokenType().equals("Bearer")
        ));
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_returnsNewTokensWithoutFetchingUser() {
        when(authService.refreshToken("old-refresh-tok")).thenReturn(
                new TokenResponseDTO("new-access", "new-refresh", 600, 3600, "Bearer")
        );

        RefreshTokenRequest request = RefreshTokenRequest.newBuilder()
                .setRefreshToken("old-refresh-tok")
                .build();
        service.refreshToken(request, responseObserver);

        verify(authValidator).validateRefreshToken("old-refresh-tok");
        verify(authService).refreshToken("old-refresh-tok");
        verify(responseObserver).onNext(argThat(resp ->
                resp.getAccessToken().equals("new-access") &&
                resp.getRefreshToken().equals("new-refresh") &&
                resp.getExpiresIn() == 600 &&
                resp.getRefreshExpiresIn() == 3600 &&
                resp.getTokenType().equals("Bearer")
        ));
        verify(responseObserver).onCompleted();

        // Verify user info is NOT fetched during token refresh
        verify(keycloakUserService, never()).getUserById(anyString());
        verify(keycloakUserService, never()).getUserByUsername(anyString());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_whenSuccessful_returnsSuccessTrue() {
        when(authService.logout("refresh-tok")).thenReturn(true);

        LogoutRequest request = LogoutRequest.newBuilder().setRefreshToken("refresh-tok").build();
        service.logout(request, logoutObserver);

        verify(authService).logout("refresh-tok");
        verify(logoutObserver).onNext(argThat(resp -> resp.getSuccess()));
        verify(logoutObserver).onCompleted();
    }

    @Test
    void logout_whenFailed_returnsSuccessFalse() {
        when(authService.logout("bad-token")).thenReturn(false);

        LogoutRequest request = LogoutRequest.newBuilder().setRefreshToken("bad-token").build();
        service.logout(request, logoutObserver);

        verify(logoutObserver).onNext(argThat(resp -> !resp.getSuccess()));
        verify(logoutObserver).onCompleted();
    }

    @Test
    void logout_whenJtiAndExpiryPresent_blacklistsToken() {
        when(authService.logout(anyString())).thenReturn(true);

        LogoutRequest request = LogoutRequest.newBuilder().setRefreshToken("refresh-tok").build();
        service.logout(request, logoutObserver);

        // jti-123 and expiry were set in @BeforeEach context
        verify(tokenBlacklistService).blacklist(eq("jti-123"), any(Instant.class));
    }

    @Test
    void logout_whenJtiIsNull_doesNotBlacklistToken() {
        // Detach the default context and attach one without JWT_ID
        grpcCtx.detach(previousContext);
        grpcCtx.cancel(null);

        grpcCtx = Context.current()
                .withValue(GrpcUserContext.USER_ID, "u-1")
                .withValue(GrpcUserContext.JWT_EXPIRY, Instant.now().plusSeconds(300))
                .withCancellation();
        previousContext = grpcCtx.attach();

        when(authService.logout(anyString())).thenReturn(true);

        LogoutRequest request = LogoutRequest.newBuilder().setRefreshToken("refresh-tok").build();
        service.logout(request, logoutObserver);

        verify(tokenBlacklistService, never()).blacklist(any(), any());
        verify(logoutObserver).onCompleted();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserRepresentation makeUser(String id, String username) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }
}

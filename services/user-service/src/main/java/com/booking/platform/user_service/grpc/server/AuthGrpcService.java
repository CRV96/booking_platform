package com.booking.platform.user_service.grpc.server;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.user_service.dto.TokenResponseDTO;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.security.PublicEndpoint;
import com.booking.platform.common.security.TokenBlacklistService;
import com.booking.platform.user_service.mapper.AttributeMapper;
import com.booking.platform.user_service.mapper.UserGrpcMapper;
import com.booking.platform.user_service.service.AuthService;
import com.booking.platform.user_service.service.KeycloakUserService;
import com.booking.platform.user_service.validation.AuthValidator;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * gRPC service implementation for authentication operations.
 * Handles user registration, login, token refresh, and logout via Keycloak.
 *
 * Exception handling is delegated to {@link com.booking.platform.common.grpc.interceptor.GrpcExceptionInterceptor}
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final KeycloakUserService keycloakUserService;
    private final UserGrpcMapper userGrpcMapper;
    private final AttributeMapper attributeMapper;
    private final AuthValidator authValidator;
    private final TokenBlacklistService tokenBlacklistService;

    @PublicEndpoint
    @Override
    public void register(RegisterRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.debug("gRPC Register request for email: {}", request.getEmail());

        authValidator.validateRegisterRequest(request);

        Map<String, String> attributes = attributeMapper.fromRegisterRequest(request);

        String userId = keycloakUserService.createUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                attributes
        );

        // Auto-login: get tokens for the new user
        TokenResponseDTO tokens = authService.login(request.getEmail(), request.getPassword());

        UserRepresentation user = keycloakUserService.getUserById(userId);
        List<String> roles = keycloakUserService.getUserRoles(userId);

        AuthResponse response = buildAuthResponse(tokens, user, roles);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.debug("User registered successfully: {}", userId);
    }

    @PublicEndpoint
    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.debug("gRPC Login request for user: {}", request.getUsername());

        authValidator.validateLoginRequest(request);

        TokenResponseDTO tokens = authService.login(request.getUsername(), request.getPassword());

        UserRepresentation user = keycloakUserService.getUserByUsername(request.getUsername());
        List<String> roles = keycloakUserService.getUserRoles(user.getId());

        AuthResponse response = buildAuthResponse(tokens, user, roles);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.debug("Login successful for user: {}", request.getUsername());
    }

    @PublicEndpoint
    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.debug("gRPC RefreshToken request");

        authValidator.validateRefreshToken(request.getRefreshToken());

        TokenResponseDTO tokens = authService.refreshToken(request.getRefreshToken());

        AuthResponse response = AuthResponse.newBuilder()
                .setAccessToken(tokens.accessToken())
                .setRefreshToken(tokens.refreshToken())
                .setExpiresIn(tokens.expiresIn())
                .setRefreshExpiresIn(tokens.refreshExpiresIn())
                .setTokenType(tokens.tokenType())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.debug("Token refresh successful");
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        log.debug("gRPC Logout request for user: {}", GrpcUserContext.getUserId());

        // Blacklist the current access token
        String jti = GrpcUserContext.getJwtId();
        Instant expiry = GrpcUserContext.getJwtExpiry();
        if (jti != null && expiry != null) {
            tokenBlacklistService.blacklist(jti, expiry);
        }

        // Invalidate refresh token in Keycloak
        boolean success = authService.logout(request.getRefreshToken());

        LogoutResponse response = LogoutResponse.newBuilder()
                .setSuccess(success)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.debug("Logout successful, token blacklisted: {}", jti);
    }

    private AuthResponse buildAuthResponse(TokenResponseDTO tokens, UserRepresentation user, List<String> roles) {
        return AuthResponse.newBuilder()
                .setAccessToken(tokens.accessToken())
                .setRefreshToken(tokens.refreshToken())
                .setExpiresIn(tokens.expiresIn())
                .setRefreshExpiresIn(tokens.refreshExpiresIn())
                .setTokenType(tokens.tokenType())
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();
    }
}

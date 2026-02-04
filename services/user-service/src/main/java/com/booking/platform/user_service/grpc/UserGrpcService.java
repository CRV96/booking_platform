package com.booking.platform.user_service.grpc;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.user_service.mapper.AttributeMapper;
import com.booking.platform.user_service.mapper.UserGrpcMapper;
import com.booking.platform.user_service.service.AuthService;
import com.booking.platform.user_service.service.AuthService.TokenResponse;
import com.booking.platform.user_service.service.KeycloakUserService;
import com.booking.platform.user_service.validation.UserValidator;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;

/**
 * gRPC service implementation for user operations.
 * Handles authentication (via Keycloak) and user management.
 *
 * Exception handling is delegated to {@link com.booking.platform.user_service.grpc.interceptor.GrpcExceptionInterceptor}
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final AuthService authService;
    private final KeycloakUserService keycloakUserService;
    private final UserGrpcMapper userGrpcMapper;
    private final AttributeMapper attributeMapper;
    private final UserValidator userValidator;

    // =========================================================================
    // AUTHENTICATION OPERATIONS
    // =========================================================================

    @Override
    public void register(RegisterRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.info("gRPC Register request for email: {}", request.getEmail());

        userValidator.validateRegisterRequest(request);

        Map<String, String> attributes = attributeMapper.fromRegisterRequest(request);

        String userId = keycloakUserService.createUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                attributes
        );

        // Auto-login: get tokens for the new user
        TokenResponse tokens = authService.login(request.getEmail(), request.getPassword());

        UserRepresentation user = keycloakUserService.getUserById(userId);
        List<String> roles = keycloakUserService.getUserRoles(userId);

        AuthResponse response = buildAuthResponse(tokens, user, roles);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.info("User registered successfully: {}", userId);
    }

    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.info("gRPC Login request for user: {}", request.getUsername());

        userValidator.validateLoginRequest(request);

        TokenResponse tokens = authService.login(request.getUsername(), request.getPassword());

        UserRepresentation user = keycloakUserService.getUserByUsername(request.getUsername());
        List<String> roles = keycloakUserService.getUserRoles(user.getId());

        AuthResponse response = buildAuthResponse(tokens, user, roles);

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.info("Login successful for user: {}", request.getUsername());
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.debug("gRPC RefreshToken request");

        userValidator.validateRefreshToken(request.getRefreshToken());

        TokenResponse tokens = authService.refreshToken(request.getRefreshToken());

        AuthResponse response = AuthResponse.newBuilder()
                .setAccessToken(tokens.access_token())
                .setRefreshToken(tokens.refresh_token())
                .setExpiresIn(tokens.expires_in())
                .setRefreshExpiresIn(tokens.refresh_expires_in())
                .setTokenType(tokens.token_type())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.debug("Token refresh successful");
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        log.debug("gRPC Logout request");

        boolean success = authService.logout(request.getRefreshToken());

        LogoutResponse response = LogoutResponse.newBuilder()
                .setSuccess(success)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // =========================================================================
    // USER PROFILE OPERATIONS
    // =========================================================================

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUser request for ID: {}", request.getUserId());

        userValidator.validateUserId(request.getUserId());

        UserRepresentation user = keycloakUserService.getUserById(request.getUserId());
        List<String> roles = keycloakUserService.getUserRoles(request.getUserId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserByUsername request: {}", request.getUsername());

        userValidator.validateUsername(request.getUsername());

        UserRepresentation user = keycloakUserService.getUserByUsername(request.getUsername());
        List<String> roles = keycloakUserService.getUserRoles(user.getId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserByEmail request: {}", request.getEmail());

        userValidator.validateEmail(request.getEmail());

        UserRepresentation user = keycloakUserService.getUserByEmail(request.getEmail());
        List<String> roles = keycloakUserService.getUserRoles(user.getId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("gRPC UpdateUser request for ID: {}", request.getUserId());

        userValidator.validateUpdateUserRequest(request);

        Map<String, String> attributes = attributeMapper.fromUpdateRequest(request);

        UserRepresentation user = keycloakUserService.updateUser(
                request.getUserId(),
                request.hasFirstName() ? request.getFirstName() : null,
                request.hasLastName() ? request.getLastName() : null,
                request.hasEmail() ? request.getEmail() : null,
                attributes
        );

        List<String> roles = keycloakUserService.getUserRoles(request.getUserId());

        UserResponse response = UserResponse.newBuilder()
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
        log.info("User updated successfully: {}", request.getUserId());
    }

    @Override
    public void searchUsers(SearchUsersRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        log.debug("gRPC SearchUsers request: query='{}', page={}, size={}",
                request.hasQuery() ? request.getQuery() : "", request.getPage(), request.getPageSize());

        String query = request.hasQuery() ? request.getQuery() : null;
        int page = request.getPage();
        int pageSize = clampPageSize(request.getPageSize());

        List<UserRepresentation> users = keycloakUserService.searchUsers(query, page, pageSize);
        int totalCount = keycloakUserService.getUserCount(query);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        SearchUsersResponse.Builder responseBuilder = SearchUsersResponse.newBuilder()
                .setTotalCount(totalCount)
                .setPage(page)
                .setPageSize(pageSize)
                .setTotalPages(totalPages);

        for (UserRepresentation user : users) {
            List<String> roles = keycloakUserService.getUserRoles(user.getId());
            responseBuilder.addUsers(userGrpcMapper.toUserInfo(user, roles));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================

    private AuthResponse buildAuthResponse(TokenResponse tokens, UserRepresentation user, List<String> roles) {
        return AuthResponse.newBuilder()
                .setAccessToken(tokens.access_token())
                .setRefreshToken(tokens.refresh_token())
                .setExpiresIn(tokens.expires_in())
                .setRefreshExpiresIn(tokens.refresh_expires_in())
                .setTokenType(tokens.token_type())
                .setUser(userGrpcMapper.toUserInfo(user, roles))
                .build();
    }

    private int clampPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}

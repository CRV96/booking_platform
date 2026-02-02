package com.booking.platform.user_service.grpc;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.user_service.exception.*;
import com.booking.platform.user_service.service.KeycloakAuthService;
import com.booking.platform.user_service.service.KeycloakAuthService.TokenResponse;
import com.booking.platform.user_service.service.KeycloakUserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * gRPC service implementation for user operations.
 * Handles authentication (via Keycloak) and user management.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final KeycloakAuthService authService;
    private final KeycloakUserService userService;

    // =========================================================================
    // AUTHENTICATION OPERATIONS
    // =========================================================================

    @Override
    public void register(RegisterRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.info("gRPC Register request for email: {}", request.getEmail());

        try {
            // Build custom attributes map
            Map<String, String> attributes = new HashMap<>();
            if (request.hasPhoneNumber()) {
                attributes.put("phoneNumber", request.getPhoneNumber());
            }
            if (request.hasCountry()) {
                attributes.put("country", request.getCountry());
            }
            if (request.hasPreferredLanguage()) {
                attributes.put("preferredLanguage", request.getPreferredLanguage());
            }

            // Create user in Keycloak
            String userId = userService.createUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                attributes
            );

            // Auto-login: get tokens for the new user
            TokenResponse tokens = authService.login(request.getEmail(), request.getPassword());

            // Get user info
            UserRepresentation user = userService.getUserById(userId);
            List<String> roles = userService.getUserRoles(userId);

            // Build response
            AuthResponse response = AuthResponse.newBuilder()
                .setAccessToken(tokens.access_token())
                .setRefreshToken(tokens.refresh_token())
                .setExpiresIn(tokens.expires_in())
                .setRefreshExpiresIn(tokens.refresh_expires_in())
                .setTokenType(tokens.token_type())
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("User registered successfully: {}", userId);

        } catch (UserAlreadyExistsException e) {
            log.warn("Registration failed - user exists: {}", request.getEmail());
            responseObserver.onError(Status.ALREADY_EXISTS
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Registration failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Registration failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.info("gRPC Login request for user: {}", request.getUsername());

        try {
            // Authenticate with Keycloak
            TokenResponse tokens = authService.login(request.getUsername(), request.getPassword());

            // Get user info
            UserRepresentation user = userService.getUserByUsername(request.getUsername());
            List<String> roles = userService.getUserRoles(user.getId());

            // Build response
            AuthResponse response = AuthResponse.newBuilder()
                .setAccessToken(tokens.access_token())
                .setRefreshToken(tokens.refresh_token())
                .setExpiresIn(tokens.expires_in())
                .setRefreshExpiresIn(tokens.refresh_expires_in())
                .setTokenType(tokens.token_type())
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Login successful for user: {}", request.getUsername());

        } catch (InvalidCredentialsException e) {
            log.warn("Login failed - invalid credentials: {}", request.getUsername());
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (UserNotFoundException e) {
            log.warn("Login failed - user not found: {}", request.getUsername());
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Login failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Login failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        log.debug("gRPC RefreshToken request");

        try {
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

        } catch (InvalidTokenException e) {
            log.warn("Token refresh failed - invalid token");
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Token refresh failed: " + e.getMessage())
                .asRuntimeException());
        }
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

        try {
            UserRepresentation user = userService.getUserById(request.getUserId());
            List<String> roles = userService.getUserRoles(request.getUserId());

            UserResponse response = UserResponse.newBuilder()
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (UserNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("GetUser failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to get user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserByUsername request: {}", request.getUsername());

        try {
            UserRepresentation user = userService.getUserByUsername(request.getUsername());
            List<String> roles = userService.getUserRoles(user.getId());

            UserResponse response = UserResponse.newBuilder()
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (UserNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("GetUserByUsername failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to get user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserByEmail request: {}", request.getEmail());

        try {
            UserRepresentation user = userService.getUserByEmail(request.getEmail());
            List<String> roles = userService.getUserRoles(user.getId());

            UserResponse response = UserResponse.newBuilder()
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (UserNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("GetUserByEmail failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to get user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("gRPC UpdateUser request for ID: {}", request.getUserId());

        try {
            // Build attributes map from request
            Map<String, String> attributes = new HashMap<>();
            if (request.hasPhoneNumber()) {
                attributes.put("phoneNumber", request.getPhoneNumber());
            }
            if (request.hasCountry()) {
                attributes.put("country", request.getCountry());
            }
            if (request.hasPreferredLanguage()) {
                attributes.put("preferredLanguage", request.getPreferredLanguage());
            }
            if (request.hasPreferredCurrency()) {
                attributes.put("preferredCurrency", request.getPreferredCurrency());
            }
            if (request.hasTimezone()) {
                attributes.put("timezone", request.getTimezone());
            }
            if (request.hasProfilePictureUrl()) {
                attributes.put("profilePictureUrl", request.getProfilePictureUrl());
            }
            if (request.hasEmailNotifications()) {
                attributes.put("emailNotifications", String.valueOf(request.getEmailNotifications()));
            }
            if (request.hasSmsNotifications()) {
                attributes.put("smsNotifications", String.valueOf(request.getSmsNotifications()));
            }

            UserRepresentation user = userService.updateUser(
                request.getUserId(),
                request.hasFirstName() ? request.getFirstName() : null,
                request.hasLastName() ? request.getLastName() : null,
                request.hasEmail() ? request.getEmail() : null,
                attributes
            );

            List<String> roles = userService.getUserRoles(request.getUserId());

            UserResponse response = UserResponse.newBuilder()
                .setUser(mapToUserInfo(user, roles))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("User updated successfully: {}", request.getUserId());

        } catch (UserNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            log.error("UpdateUser failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to update user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void searchUsers(SearchUsersRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        log.debug("gRPC SearchUsers request: query='{}', page={}, size={}",
            request.hasQuery() ? request.getQuery() : "", request.getPage(), request.getPageSize());

        try {
            String query = request.hasQuery() ? request.getQuery() : null;
            int page = request.getPage();
            int pageSize = Math.min(Math.max(request.getPageSize(), 1), 100); // Clamp between 1-100

            List<UserRepresentation> users = userService.searchUsers(query, page, pageSize);
            int totalCount = userService.getUserCount(query);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            SearchUsersResponse.Builder responseBuilder = SearchUsersResponse.newBuilder()
                .setTotalCount(totalCount)
                .setPage(page)
                .setPageSize(pageSize)
                .setTotalPages(totalPages);

            for (UserRepresentation user : users) {
                List<String> roles = userService.getUserRoles(user.getId());
                responseBuilder.addUsers(mapToUserInfo(user, roles));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("SearchUsers failed", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to search users: " + e.getMessage())
                .asRuntimeException());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Maps a Keycloak UserRepresentation to our gRPC UserInfo message.
     */
    private UserInfo mapToUserInfo(UserRepresentation user, List<String> roles) {
        UserInfo.Builder builder = UserInfo.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername() != null ? user.getUsername() : "")
            .setEmail(user.getEmail() != null ? user.getEmail() : "")
            .setEmailVerified(user.isEmailVerified() != null ? user.isEmailVerified() : false)
            .setEnabled(user.isEnabled() != null ? user.isEnabled() : false)
            .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
            .setLastName(user.getLastName() != null ? user.getLastName() : "")
            .setCreatedAt(user.getCreatedTimestamp() != null
                ? Instant.ofEpochMilli(user.getCreatedTimestamp()).toString()
                : "")
            .addAllRoles(roles);

        // Map custom attributes
        Map<String, List<String>> attrs = user.getAttributes();
        if (attrs != null) {
            setIfPresent(builder, attrs, "phoneNumber", UserInfo.Builder::setPhoneNumber);
            setIfPresent(builder, attrs, "country", UserInfo.Builder::setCountry);
            setIfPresent(builder, attrs, "preferredLanguage", UserInfo.Builder::setPreferredLanguage);
            setIfPresent(builder, attrs, "preferredCurrency", UserInfo.Builder::setPreferredCurrency);
            setIfPresent(builder, attrs, "timezone", UserInfo.Builder::setTimezone);
            setIfPresent(builder, attrs, "profilePictureUrl", UserInfo.Builder::setProfilePictureUrl);

            // Boolean attributes
            if (attrs.containsKey("emailNotifications")) {
                builder.setEmailNotifications(Boolean.parseBoolean(attrs.get("emailNotifications").get(0)));
            }
            if (attrs.containsKey("smsNotifications")) {
                builder.setSmsNotifications(Boolean.parseBoolean(attrs.get("smsNotifications").get(0)));
            }
        }

        return builder.build();
    }

    private void setIfPresent(UserInfo.Builder builder, Map<String, List<String>> attrs,
                              String key, java.util.function.BiConsumer<UserInfo.Builder, String> setter) {
        if (attrs.containsKey(key) && !attrs.get(key).isEmpty()) {
            setter.accept(builder, attrs.get(key).get(0));
        }
    }
}

package com.booking.platform.graphql_gateway.client;

import com.booking.platform.common.grpc.user.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * gRPC client wrapper for the User Service.
 * Provides a clean API for calling user-service from the gateway.
 */
@Component
@Slf4j
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * Register a new user.
     */
    public AuthResponse register(String email, String password, String firstName, String lastName,
                                  String phoneNumber, String country, String preferredLanguage) {
        log.debug("Calling user-service: Register for {}", email);

        RegisterRequest.Builder builder = RegisterRequest.newBuilder()
            .setEmail(email)
            .setPassword(password)
            .setFirstName(firstName)
            .setLastName(lastName);

        if (phoneNumber != null) builder.setPhoneNumber(phoneNumber);
        if (country != null) builder.setCountry(country);
        if (preferredLanguage != null) builder.setPreferredLanguage(preferredLanguage);

        return userServiceStub.register(builder.build());
    }

    /**
     * Login with username/password.
     */
    public AuthResponse login(String username, String password) {
        log.debug("Calling user-service: Login for {}", username);

        LoginRequest request = LoginRequest.newBuilder()
            .setUsername(username)
            .setPassword(password)
            .build();

        return userServiceStub.login(request);
    }

    /**
     * Refresh access token.
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Calling user-service: RefreshToken");

        RefreshTokenRequest request = RefreshTokenRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        return userServiceStub.refreshToken(request);
    }

    /**
     * Logout (invalidate refresh token).
     */
    public boolean logout(String refreshToken) {
        log.debug("Calling user-service: Logout");

        LogoutRequest request = LogoutRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        LogoutResponse response = userServiceStub.logout(request);
        return response.getSuccess();
    }

    /**
     * Get user by ID.
     */
    public UserInfo getUser(String userId) {
        log.debug("Calling user-service: GetUser {}", userId);

        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(userId)
            .build();

        UserResponse response = userServiceStub.getUser(request);
        return response.getUser();
    }

    /**
     * Get user by username.
     */
    public UserInfo getUserByUsername(String username) {
        log.debug("Calling user-service: GetUserByUsername {}", username);

        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
            .setUsername(username)
            .build();

        UserResponse response = userServiceStub.getUserByUsername(request);
        return response.getUser();
    }

    /**
     * Update user profile.
     */
    public UserInfo updateUser(String userId, String firstName, String lastName, String email,
                               String phoneNumber, String country, String preferredLanguage,
                               String preferredCurrency, String timezone, String profilePictureUrl,
                               Boolean emailNotifications, Boolean smsNotifications) {
        log.debug("Calling user-service: UpdateUser {}", userId);

        UpdateUserRequest.Builder builder = UpdateUserRequest.newBuilder()
            .setUserId(userId);

        if (firstName != null) builder.setFirstName(firstName);
        if (lastName != null) builder.setLastName(lastName);
        if (email != null) builder.setEmail(email);
        if (phoneNumber != null) builder.setPhoneNumber(phoneNumber);
        if (country != null) builder.setCountry(country);
        if (preferredLanguage != null) builder.setPreferredLanguage(preferredLanguage);
        if (preferredCurrency != null) builder.setPreferredCurrency(preferredCurrency);
        if (timezone != null) builder.setTimezone(timezone);
        if (profilePictureUrl != null) builder.setProfilePictureUrl(profilePictureUrl);
        if (emailNotifications != null) builder.setEmailNotifications(emailNotifications);
        if (smsNotifications != null) builder.setSmsNotifications(smsNotifications);

        UserResponse response = userServiceStub.updateUser(builder.build());
        return response.getUser();
    }

    /**
     * Search users with pagination.
     */
    public SearchUsersResponse searchUsers(String query, int page, int pageSize) {
        log.debug("Calling user-service: SearchUsers query='{}', page={}, size={}", query, page, pageSize);

        SearchUsersRequest.Builder builder = SearchUsersRequest.newBuilder()
            .setPage(page)
            .setPageSize(pageSize);

        if (query != null) builder.setQuery(query);

        return userServiceStub.searchUsers(builder.build());
    }
}

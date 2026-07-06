package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.graphql_gateway.grpc.client.UserOperationsClient;
import com.booking.platform.graphql_gateway.constants.UserServiceConst;
import lombok.RequiredArgsConstructor;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserOperationsClient {

    @GrpcClient(UserServiceConst.GRPC_CLIENT)
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @Override
    public UserInfo getUser(String userId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling user-service: GetUser {}", userId);

        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        UserResponse response = userServiceStub.getUser(request);
        return response.getUser();
    }

    @Override
    public UserInfo getUserByUsername(String username) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling user-service: GetUserByUsername {}", username);

        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
                .setUsername(username)
                .build();

        UserResponse response = userServiceStub.getUserByUsername(request);
        return response.getUser();
    }

    @Override
    public UserInfo updateUser(String userId, String firstName, String lastName, String email,
                               String phoneNumber, String country, String preferredLanguage,
                               String preferredCurrency, String timezone, String profilePictureUrl,
                               Boolean emailNotifications, Boolean smsNotifications) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling user-service: UpdateUser {}", userId);

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

    @Override
    public SearchUsersResponse searchUsers(String query, int page, int pageSize) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling user-service: SearchUsers query='{}', page={}, size={}", query, page, pageSize);

        SearchUsersRequest.Builder builder = SearchUsersRequest.newBuilder()
                .setPage(page)
                .setPageSize(pageSize);

        if (query != null) builder.setQuery(query);

        return userServiceStub.searchUsers(builder.build());
    }
}

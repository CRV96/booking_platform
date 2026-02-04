package com.booking.platform.graphql_gateway.client.impl;

import com.booking.platform.common.grpc.user.*;
import com.booking.platform.graphql_gateway.client.AuthClient;
import com.booking.platform.graphql_gateway.constants.UserServiceConst;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserServiceAuthClientImpl implements AuthClient {

    @GrpcClient(UserServiceConst.UserGRPCConst.USER_SERVICE_GRPC_CLIENT)
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @Override
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

    @Override
    public AuthResponse login(String username, String password) {
        log.debug("Calling user-service: Login for {}", username);

        LoginRequest request = LoginRequest.newBuilder()
            .setUsername(username)
            .setPassword(password)
            .build();

        return userServiceStub.login(request);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Calling user-service: RefreshToken");

        RefreshTokenRequest request = RefreshTokenRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        return userServiceStub.refreshToken(request);
    }

    @Override
    public boolean logout(String refreshToken) {
        log.debug("Calling user-service: Logout");

        LogoutRequest request = LogoutRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        LogoutResponse response = userServiceStub.logout(request);
        return response.getSuccess();
    }

}

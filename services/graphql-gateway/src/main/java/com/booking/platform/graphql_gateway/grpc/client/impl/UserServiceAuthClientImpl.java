package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.user.AuthResponse;
import com.booking.platform.common.grpc.user.AuthServiceGrpc;
import com.booking.platform.common.grpc.user.LoginRequest;
import com.booking.platform.common.grpc.user.LogoutRequest;
import com.booking.platform.common.grpc.user.LogoutResponse;
import com.booking.platform.common.grpc.user.RefreshTokenRequest;
import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.graphql_gateway.grpc.client.AuthClient;
import com.booking.platform.graphql_gateway.constants.UserServiceConst;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserServiceAuthClientImpl implements AuthClient {

    @GrpcClient(UserServiceConst.GRPC_CLIENT)
    private AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

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

        return authServiceStub.register(builder.build());
    }

    @Override
    public AuthResponse login(String username, String password) {
        log.debug("Calling user-service: Login for {}", username);

        LoginRequest request = LoginRequest.newBuilder()
            .setUsername(username)
            .setPassword(password)
            .build();

        return authServiceStub.login(request);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Calling user-service: RefreshToken");

        RefreshTokenRequest request = RefreshTokenRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        return authServiceStub.refreshToken(request);
    }

    @Override
    public boolean logout(String refreshToken) {
        log.debug("Calling user-service: Logout");

        LogoutRequest request = LogoutRequest.newBuilder()
            .setRefreshToken(refreshToken)
            .build();

        LogoutResponse response = authServiceStub.logout(request);
        return response.getSuccess();
    }

}

package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.user.AuthResponse;

public interface AuthClient {

    AuthResponse register(String email, String password, String firstName, String lastName,
                          String phoneNumber, String country, String preferredLanguage);

    AuthResponse login(String username, String password);

    boolean logout(String refreshToken);

    AuthResponse refreshToken(String refreshToken);
}

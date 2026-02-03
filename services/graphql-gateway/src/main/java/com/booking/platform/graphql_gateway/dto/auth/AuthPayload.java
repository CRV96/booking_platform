package com.booking.platform.graphql_gateway.dto.auth;

import com.booking.platform.common.grpc.user.AuthResponse;
import com.booking.platform.graphql_gateway.dto.user.User;

public record AuthPayload(
    String accessToken,
    String refreshToken,
    int expiresIn,
    int refreshExpiresIn,
    String tokenType,
    User user
) {
    public static AuthPayload fromGrpc(AuthResponse response) {
        return new AuthPayload(
            response.getAccessToken(),
            response.getRefreshToken(),
            response.getExpiresIn(),
            response.getRefreshExpiresIn(),
            response.getTokenType(),
            response.hasUser() ? User.fromGrpc(response.getUser()) : null
        );
    }
}

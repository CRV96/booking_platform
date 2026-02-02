package com.booking.platform.graphql_gateway.dto;

public record AuthPayload(
    String accessToken,
    String refreshToken,
    int expiresIn,
    int refreshExpiresIn,
    String tokenType,
    User user
) {}

package com.booking.platform.graphql_gateway.dto.auth;

public record LoginInput(
    String username,
    String password
) {}

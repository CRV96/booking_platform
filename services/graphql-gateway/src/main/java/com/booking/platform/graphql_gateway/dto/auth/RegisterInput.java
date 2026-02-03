package com.booking.platform.graphql_gateway.dto.auth;

public record RegisterInput(
    String email,
    String password,
    String firstName,
    String lastName,
    String phoneNumber,
    String country,
    String preferredLanguage
) {}

package com.booking.platform.graphql_gateway.dto;

public record UpdateProfileInput(
    String firstName,
    String lastName,
    String email,
    String phoneNumber,
    String country,
    String preferredLanguage,
    String preferredCurrency,
    String timezone,
    String profilePictureUrl,
    Boolean emailNotifications,
    Boolean smsNotifications
) {}

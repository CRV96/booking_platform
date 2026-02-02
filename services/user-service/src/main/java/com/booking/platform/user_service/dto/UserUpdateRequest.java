package com.booking.platform.user_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Size(max = 255)
    String firstName,

    @Size(max = 255)
    String lastName,

    @Email
    @Size(max = 255)
    String email,

    @Size(max = 20)
    String phoneNumber,

    @Size(max = 100)
    String country,

    @Size(max = 10)
    String preferredLanguage,

    String dateOfBirth,

    @Size(max = 500)
    String billingAddress,

    @Size(max = 500)
    String profilePictureUrl,

    @Size(max = 3)
    String preferredCurrency,

    @Size(max = 50)
    String timezone,

    Boolean emailNotifications,

    Boolean smsNotifications,

    @Size(max = 2000)
    String favoriteEventIds,

    @Size(max = 2000)
    String favoriteVenueIds
) {}

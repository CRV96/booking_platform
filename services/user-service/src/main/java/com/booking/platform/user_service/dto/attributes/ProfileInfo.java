package com.booking.platform.user_service.dto.attributes;

import lombok.Builder;

/**
 * User profile information.
 */
@Builder
public record ProfileInfo(
    String firstName,
    String lastName,
    String phoneNumber,
    String country,
    String dateOfBirth,
    String billingAddress,
    String profilePictureUrl
) {

    public static ProfileInfo empty() {
        return ProfileInfo.builder().build();
    }
}

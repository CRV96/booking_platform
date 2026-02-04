package com.booking.platform.user_service.dto.attributes;

import lombok.Builder;

/**
 * User preferences and notification settings.
 */
@Builder
public record PreferencesInfo(
    String preferredLanguage,
    String preferredCurrency,
    String timezone,
    Boolean emailNotifications,
    Boolean smsNotifications,
    String favoriteEventIds,
    String favoriteVenueIds
) {

    public static PreferencesInfo empty() {
        return PreferencesInfo.builder().build();
    }
}

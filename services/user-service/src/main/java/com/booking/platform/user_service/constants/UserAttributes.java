package com.booking.platform.user_service.constants;

/**
 * Constants for user attribute names stored in Keycloak.
 */
public interface UserAttributes {

    // Contact Information
    String PHONE_NUMBER = "phoneNumber";
    String COUNTRY = "country";

    // Preferences
    String PREFERRED_LANGUAGE = "preferredLanguage";
    String PREFERRED_CURRENCY = "preferredCurrency";
    String TIMEZONE = "timezone";

    // Profile
    String DATE_OF_BIRTH = "dateOfBirth";
    String BILLING_ADDRESS = "billingAddress";
    String PROFILE_PICTURE_URL = "profilePictureUrl";

    // Notification Settings
    String EMAIL_NOTIFICATIONS = "emailNotifications";
    String SMS_NOTIFICATIONS = "smsNotifications";

    // Favorites
    String FAVORITE_EVENT_IDS = "favoriteEventIds";
    String FAVORITE_VENUE_IDS = "favoriteVenueIds";
}

package com.booking.platform.user_service.constants;

/**
 * Constants for user attribute names stored in Keycloak.
 */
public final class UserAttributes {

    private UserAttributes() {}

    // Contact Information
    public static final String PHONE_NUMBER = "phoneNumber";
    public static final String COUNTRY = "country";

    // Preferences
    public static final String PREFERRED_LANGUAGE = "preferredLanguage";
    public static final String PREFERRED_CURRENCY = "preferredCurrency";
    public static final String TIMEZONE = "timezone";

    // Profile
    public static final String DATE_OF_BIRTH = "dateOfBirth";
    public static final String BILLING_ADDRESS = "billingAddress";
    public static final String PROFILE_PICTURE_URL = "profilePictureUrl";

    // Notification Settings
    public static final String EMAIL_NOTIFICATIONS = "emailNotifications";
    public static final String SMS_NOTIFICATIONS = "smsNotifications";

    // Favorites
    public static final String FAVORITE_EVENT_IDS = "favoriteEventIds";
    public static final String FAVORITE_VENUE_IDS = "favoriteVenueIds";
}

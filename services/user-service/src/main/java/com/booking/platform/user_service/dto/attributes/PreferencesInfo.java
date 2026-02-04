package com.booking.platform.user_service.dto.attributes;

/**
 * User preferences and notification settings.
 */
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
        return new PreferencesInfo(null, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String preferredLanguage;
        private String preferredCurrency;
        private String timezone;
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private String favoriteEventIds;
        private String favoriteVenueIds;

        public Builder preferredLanguage(String preferredLanguage) {
            this.preferredLanguage = preferredLanguage;
            return this;
        }

        public Builder preferredCurrency(String preferredCurrency) {
            this.preferredCurrency = preferredCurrency;
            return this;
        }

        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }

        public Builder emailNotifications(Boolean emailNotifications) {
            this.emailNotifications = emailNotifications;
            return this;
        }

        public Builder smsNotifications(Boolean smsNotifications) {
            this.smsNotifications = smsNotifications;
            return this;
        }

        public Builder favoriteEventIds(String favoriteEventIds) {
            this.favoriteEventIds = favoriteEventIds;
            return this;
        }

        public Builder favoriteVenueIds(String favoriteVenueIds) {
            this.favoriteVenueIds = favoriteVenueIds;
            return this;
        }

        public PreferencesInfo build() {
            return new PreferencesInfo(
                preferredLanguage, preferredCurrency, timezone,
                emailNotifications, smsNotifications,
                favoriteEventIds, favoriteVenueIds
            );
        }
    }
}

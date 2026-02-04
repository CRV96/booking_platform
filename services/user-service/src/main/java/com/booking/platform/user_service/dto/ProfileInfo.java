package com.booking.platform.user_service.dto;

/**
 * User profile information.
 */
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
        return new ProfileInfo(null, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String country;
        private String dateOfBirth;
        private String billingAddress;
        private String profilePictureUrl;

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder dateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
            return this;
        }

        public Builder billingAddress(String billingAddress) {
            this.billingAddress = billingAddress;
            return this;
        }

        public Builder profilePictureUrl(String profilePictureUrl) {
            this.profilePictureUrl = profilePictureUrl;
            return this;
        }

        public ProfileInfo build() {
            return new ProfileInfo(
                firstName, lastName, phoneNumber, country,
                dateOfBirth, billingAddress, profilePictureUrl
            );
        }
    }
}

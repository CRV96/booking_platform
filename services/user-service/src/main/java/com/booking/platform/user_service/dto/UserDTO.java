package com.booking.platform.user_service.dto;

import java.time.Instant;
import java.util.List;

/**
 * User data transfer object with nested profile and preferences.
 */
public record UserDTO(
    String id,
    String username,
    String email,
    Boolean emailVerified,
    Boolean enabled,
    ProfileInfo profile,
    PreferencesInfo preferences,
    List<String> roles,
    Instant createdAt
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String username;
        private String email;
        private Boolean emailVerified;
        private Boolean enabled;
        private ProfileInfo profile;
        private PreferencesInfo preferences;
        private List<String> roles;
        private Instant createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder emailVerified(Boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder profile(ProfileInfo profile) {
            this.profile = profile;
            return this;
        }

        public Builder preferences(PreferencesInfo preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public UserDTO build() {
            return new UserDTO(
                id, username, email, emailVerified, enabled,
                profile != null ? profile : ProfileInfo.empty(),
                preferences != null ? preferences : PreferencesInfo.empty(),
                roles != null ? roles : List.of(),
                createdAt
            );
        }
    }
}

package com.booking.platform.user_service.dto;

import com.booking.platform.user_service.dto.attributes.PreferencesInfo;
import com.booking.platform.user_service.dto.attributes.ProfileInfo;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * User data transfer object with nested profile and preferences.
 */
@Builder
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

    public UserDTO {
        profile = profile != null ? profile : ProfileInfo.empty();
        preferences = preferences != null ? preferences : PreferencesInfo.empty();
        roles = roles != null ? roles : List.of();
    }
}

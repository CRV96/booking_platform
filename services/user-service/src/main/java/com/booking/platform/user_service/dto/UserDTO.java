package com.booking.platform.user_service.dto;

import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.entity.UserAttributeEntity;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public record UserDTO(
    String id,
    String username,
    String email,
    Boolean emailVerified,
    Boolean enabled,
    String firstName,
    String lastName,
    Instant createdAt,

    // Custom attributes
    String phoneNumber,
    String country,
    String preferredLanguage,
    String dateOfBirth,
    String billingAddress,
    String profilePictureUrl,
    String preferredCurrency,
    String timezone,
    Boolean emailNotifications,
    Boolean smsNotifications,
    String favoriteEventIds,
    String favoriteVenueIds
) {

    public static UserDTO fromEntity(UserEntity entity) {
        Map<String, String> attrs = entity.getAttributes().stream()
            .collect(Collectors.toMap(
                UserAttributeEntity::getName,
                UserAttributeEntity::getValue,
                (v1, v2) -> v1  // Keep first value if duplicate keys
            ));

        return new UserDTO(
            entity.getId(),
            entity.getUsername(),
            entity.getEmail(),
            entity.getEmailVerified(),
            entity.getEnabled(),
            entity.getFirstName(),
            entity.getLastName(),
            entity.getCreatedTimestamp() != null
                ? Instant.ofEpochMilli(entity.getCreatedTimestamp())
                : null,

            // Custom attributes
            attrs.get("phoneNumber"),
            attrs.get("country"),
            attrs.get("preferredLanguage"),
            attrs.get("dateOfBirth"),
            attrs.get("billingAddress"),
            attrs.get("profilePictureUrl"),
            attrs.get("preferredCurrency"),
            attrs.get("timezone"),
            parseBoolean(attrs.get("emailNotifications")),
            parseBoolean(attrs.get("smsNotifications")),
            attrs.get("favoriteEventIds"),
            attrs.get("favoriteVenueIds")
        );
    }

    private static Boolean parseBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : null;
    }
}

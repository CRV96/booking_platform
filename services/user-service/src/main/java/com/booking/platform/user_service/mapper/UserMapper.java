package com.booking.platform.user_service.mapper;

import com.booking.platform.user_service.constants.UserAttributes;
import com.booking.platform.user_service.dto.attributes.PreferencesInfo;
import com.booking.platform.user_service.dto.attributes.ProfileInfo;
import com.booking.platform.user_service.dto.UserDTO;
import com.booking.platform.user_service.entity.UserAttributeEntity;
import com.booking.platform.user_service.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper for converting between UserEntity and UserDTO.
 */
@Component
public class UserMapper {

    public UserDTO toDTO(UserEntity entity) {
        return toDTO(entity, List.of());
    }

    public UserDTO toDTO(UserEntity entity, List<String> roles) {
        Map<String, String> attrs = extractAttributes(entity.getAttributes());

        return UserDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .emailVerified(entity.getEmailVerified())
                .enabled(entity.getEnabled())
                .profile(buildProfileInfo(entity, attrs))
                .preferences(buildPreferencesInfo(attrs))
                .roles(roles)
                .createdAt(toInstant(entity.getCreatedTimestamp()))
                .build();
    }

    public List<UserDTO> toDTOList(List<UserEntity> entities) {
        return entities.stream()
                .map(this::toDTO)
                .toList();
    }

    private Map<String, String> extractAttributes(List<UserAttributeEntity> attributes) {
        if (attributes == null) {
            return Map.of();
        }

        return attributes.stream()
                .collect(Collectors.toMap(
                        UserAttributeEntity::getName,
                        UserAttributeEntity::getValue,
                        (v1, v2) -> v1  // Keep first value if duplicate keys
                ));
    }

    private ProfileInfo buildProfileInfo(UserEntity entity, Map<String, String> attrs) {
        return ProfileInfo.builder()
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phoneNumber(attrs.get(UserAttributes.PHONE_NUMBER))
                .country(attrs.get(UserAttributes.COUNTRY))
                .dateOfBirth(attrs.get(UserAttributes.DATE_OF_BIRTH))
                .billingAddress(attrs.get(UserAttributes.BILLING_ADDRESS))
                .profilePictureUrl(attrs.get(UserAttributes.PROFILE_PICTURE_URL))
                .build();
    }

    private PreferencesInfo buildPreferencesInfo(Map<String, String> attrs) {
        return PreferencesInfo.builder()
                .preferredLanguage(attrs.get(UserAttributes.PREFERRED_LANGUAGE))
                .preferredCurrency(attrs.get(UserAttributes.PREFERRED_CURRENCY))
                .timezone(attrs.get(UserAttributes.TIMEZONE))
                .emailNotifications(parseBoolean(attrs.get(UserAttributes.EMAIL_NOTIFICATIONS)))
                .smsNotifications(parseBoolean(attrs.get(UserAttributes.SMS_NOTIFICATIONS)))
                .favoriteEventIds(attrs.get(UserAttributes.FAVORITE_EVENT_IDS))
                .favoriteVenueIds(attrs.get(UserAttributes.FAVORITE_VENUE_IDS))
                .build();
    }

    private Boolean parseBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : null;
    }

    private Instant toInstant(Long timestamp) {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : null;
    }
}

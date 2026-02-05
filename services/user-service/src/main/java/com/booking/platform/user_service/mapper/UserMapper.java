package com.booking.platform.user_service.mapper;

import com.booking.platform.user_service.constants.UserAttributes;
import com.booking.platform.user_service.dto.UserDTO;
import com.booking.platform.user_service.dto.attributes.PreferencesInfo;
import com.booking.platform.user_service.dto.attributes.ProfileInfo;
import com.booking.platform.user_service.entity.UserAttributeEntity;
import com.booking.platform.user_service.entity.UserEntity;
import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting between UserEntity and UserDTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "profile", expression = "java(buildProfileInfo(entity))")
    @Mapping(target = "preferences", expression = "java(buildPreferencesInfo(entity))")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "createdAt", expression = "java(toInstant(entity.getCreatedTimestamp()))")
    UserDTO toDTO(UserEntity entity, List<String> roles);

    default UserDTO toDTO(UserEntity entity) {
        return toDTO(entity, List.of());
    }

    default List<UserDTO> toDTOList(List<UserEntity> entities) {
        return entities.stream()
                .map(this::toDTO)
                .toList();
    }

    default ProfileInfo buildProfileInfo(UserEntity entity) {
        Map<String, String> attrs = extractAttributes(entity.getAttributes());

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

    default PreferencesInfo buildPreferencesInfo(UserEntity entity) {
        Map<String, String> attrs = extractAttributes(entity.getAttributes());

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

    default Map<String, String> extractAttributes(List<UserAttributeEntity> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        return attributes.stream()
                .collect(Collectors.toMap(
                        UserAttributeEntity::getName,
                        UserAttributeEntity::getValue,
                        (v1, v2) -> v1
                ));
    }

    default Boolean parseBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : null;
    }

    default Instant toInstant(Long timestamp) {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : null;
    }
}

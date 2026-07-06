package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.constants.UserAttributes;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for extracting custom Keycloak attributes from gRPC requests.
 *
 * <p>Not all 13 attributes defined in {@link UserAttributes} are settable in every request:
 * <ul>
 *   <li><b>Registration</b> — only basic contact/locale fields: phone, country, language (3 attributes).
 *       Other profile data (billing address, DOB, etc.) is collected post-registration.</li>
 *   <li><b>Update</b> — broader set including preferences, notification settings, and profile picture
 *       (8 attributes). Favorites and remaining profile fields (DOB, billing address) are managed
 *       separately via dedicated endpoints.</li>
 * </ul>
 */
@Component
public class AttributeMapper {

    /**
     * Extracts attributes available at registration: phone, country, and preferred language.
     */
    public Map<String, String> fromRegisterRequest(RegisterRequest request) {
        Map<String, String> attributes = new HashMap<>();

        if (request.hasPhoneNumber()) {
            attributes.put(UserAttributes.PHONE_NUMBER, request.getPhoneNumber());
        }
        if (request.hasCountry()) {
            attributes.put(UserAttributes.COUNTRY, request.getCountry());
        }
        if (request.hasPreferredLanguage()) {
            attributes.put(UserAttributes.PREFERRED_LANGUAGE, request.getPreferredLanguage());
        }

        return attributes;
    }

    /**
     * Extracts attributes available for profile updates: contact info, preferences,
     * notification settings, and profile picture.
     */
    public Map<String, String> fromUpdateRequest(UpdateUserRequest request) {
        Map<String, String> attributes = new HashMap<>();

        if (request.hasPhoneNumber()) {
            attributes.put(UserAttributes.PHONE_NUMBER, request.getPhoneNumber());
        }
        if (request.hasCountry()) {
            attributes.put(UserAttributes.COUNTRY, request.getCountry());
        }
        if (request.hasPreferredLanguage()) {
            attributes.put(UserAttributes.PREFERRED_LANGUAGE, request.getPreferredLanguage());
        }
        if (request.hasPreferredCurrency()) {
            attributes.put(UserAttributes.PREFERRED_CURRENCY, request.getPreferredCurrency());
        }
        if (request.hasTimezone()) {
            attributes.put(UserAttributes.TIMEZONE, request.getTimezone());
        }
        if (request.hasProfilePictureUrl()) {
            attributes.put(UserAttributes.PROFILE_PICTURE_URL, request.getProfilePictureUrl());
        }
        if (request.hasEmailNotifications()) {
            attributes.put(UserAttributes.EMAIL_NOTIFICATIONS, String.valueOf(request.getEmailNotifications()));
        }
        if (request.hasSmsNotifications()) {
            attributes.put(UserAttributes.SMS_NOTIFICATIONS, String.valueOf(request.getSmsNotifications()));
        }

        return attributes;
    }
}

package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.RegisterRequest;
import com.booking.platform.common.grpc.user.UpdateUserRequest;
import com.booking.platform.user_service.constants.UserAttributes;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for extracting custom attributes from gRPC requests.
 */
@Component
public class AttributeMapper {

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

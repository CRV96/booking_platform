package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.UserInfo;
import com.booking.platform.user_service.constants.UserAttributes;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mapper for converting Keycloak UserRepresentation to gRPC messages.
 */
@Component
public class UserGrpcMapper {

    public UserInfo toUserInfo(UserRepresentation user, List<String> roles) {
        UserInfo.Builder builder = UserInfo.newBuilder()
                .setId(user.getId())
                .setUsername(nullToEmpty(user.getUsername()))
                .setEmail(nullToEmpty(user.getEmail()))
                .setEmailVerified(user.isEmailVerified() != null ? user.isEmailVerified() : false)
                .setEnabled(user.isEnabled() != null ? user.isEnabled() : false)
                .setFirstName(nullToEmpty(user.getFirstName()))
                .setLastName(nullToEmpty(user.getLastName()))
                .setCreatedAt(formatTimestamp(user.getCreatedTimestamp()))
                .addAllRoles(roles);

        // Map custom attributes
        mapAttributes(builder, user.getAttributes());

        return builder.build();
    }

    private void mapAttributes(UserInfo.Builder builder, Map<String, List<String>> attrs) {
        if (attrs == null) {
            return;
        }

        setIfPresent(builder, attrs, UserAttributes.PHONE_NUMBER, UserInfo.Builder::setPhoneNumber);
        setIfPresent(builder, attrs, UserAttributes.COUNTRY, UserInfo.Builder::setCountry);
        setIfPresent(builder, attrs, UserAttributes.PREFERRED_LANGUAGE, UserInfo.Builder::setPreferredLanguage);
        setIfPresent(builder, attrs, UserAttributes.PREFERRED_CURRENCY, UserInfo.Builder::setPreferredCurrency);
        setIfPresent(builder, attrs, UserAttributes.TIMEZONE, UserInfo.Builder::setTimezone);
        setIfPresent(builder, attrs, UserAttributes.PROFILE_PICTURE_URL, UserInfo.Builder::setProfilePictureUrl);

        // Boolean attributes
        setBooleanIfPresent(builder, attrs, UserAttributes.EMAIL_NOTIFICATIONS, UserInfo.Builder::setEmailNotifications);
        setBooleanIfPresent(builder, attrs, UserAttributes.SMS_NOTIFICATIONS, UserInfo.Builder::setSmsNotifications);
    }

    private void setIfPresent(
            UserInfo.Builder builder,
            Map<String, List<String>> attrs,
            String key,
            java.util.function.BiConsumer<UserInfo.Builder, String> setter) {

        if (attrs.containsKey(key) && !attrs.get(key).isEmpty()) {
            setter.accept(builder, attrs.get(key).get(0));
        }
    }

    private void setBooleanIfPresent(
            UserInfo.Builder builder,
            Map<String, List<String>> attrs,
            String key,
            java.util.function.BiConsumer<UserInfo.Builder, Boolean> setter) {

        if (attrs.containsKey(key) && !attrs.get(key).isEmpty()) {
            setter.accept(builder, Boolean.parseBoolean(attrs.get(key).get(0)));
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String formatTimestamp(Long timestamp) {
        return timestamp != null ? Instant.ofEpochMilli(timestamp).toString() : "";
    }
}

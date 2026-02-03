package com.booking.platform.graphql_gateway.dto.user;

import com.booking.platform.common.grpc.user.UserInfo;

import java.util.List;

public record User(
    String id,
    String username,
    String email,
    boolean emailVerified,
    boolean enabled,
    String firstName,
    String lastName,
    String createdAt,
    String phoneNumber,
    String country,
    String preferredLanguage,
    String preferredCurrency,
    String timezone,
    String profilePictureUrl,
    Boolean emailNotifications,
    Boolean smsNotifications,
    List<String> roles
) {
    /**
     * Maps a gRPC UserInfo to a GraphQL User DTO.
     */
    public static User fromGrpc(UserInfo userInfo) {
        return new User(
            userInfo.getId(),
            userInfo.getUsername(),
            userInfo.getEmail(),
            userInfo.getEmailVerified(),
            userInfo.getEnabled(),
            userInfo.getFirstName(),
            userInfo.getLastName(),
            userInfo.getCreatedAt(),
            userInfo.hasPhoneNumber() ? userInfo.getPhoneNumber() : null,
            userInfo.hasCountry() ? userInfo.getCountry() : null,
            userInfo.hasPreferredLanguage() ? userInfo.getPreferredLanguage() : null,
            userInfo.hasPreferredCurrency() ? userInfo.getPreferredCurrency() : null,
            userInfo.hasTimezone() ? userInfo.getTimezone() : null,
            userInfo.hasProfilePictureUrl() ? userInfo.getProfilePictureUrl() : null,
            userInfo.hasEmailNotifications() ? userInfo.getEmailNotifications() : null,
            userInfo.hasSmsNotifications() ? userInfo.getSmsNotifications() : null,
            userInfo.getRolesList()
        );
    }
}

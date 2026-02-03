package com.booking.platform.graphql_gateway.client;

import com.booking.platform.common.grpc.user.SearchUsersResponse;
import com.booking.platform.common.grpc.user.UserInfo;

public interface UserOperationsClient {

    UserInfo getUser(String userId);

    UserInfo getUserByUsername(String username);

    UserInfo updateUser(String userId, String firstName, String lastName, String email,
                        String phoneNumber, String country, String preferredLanguage,
                        String preferredCurrency, String timezone, String profilePictureUrl,
                        Boolean emailNotifications, Boolean smsNotifications);

    SearchUsersResponse searchUsers(String query, int page, int pageSize);
}

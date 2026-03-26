package com.booking.platform.notification_service.grpc.client;

import java.util.List;

public interface UserServiceClient {
    String getUserEmail(String userId);
    List<String> getUsersEmails(List<String> userIds);
}

package com.booking.platform.notification_service.grpc.client;

public interface UserServiceClient {
    String getUserEmail(String userId);
}

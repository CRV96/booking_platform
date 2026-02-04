package com.booking.platform.user_service.service;

public interface UserService<USER> {
    USER getUserById(String userId);
    USER getUserByUsername(String username);
    USER getUserByEmail(String email);
}

package com.booking.platform.user_service.service.impl;

import com.booking.platform.user_service.config.CacheConfig;
import com.booking.platform.user_service.entity.UserAttributeEntity;
import com.booking.platform.user_service.entity.UserEntity;
import com.booking.platform.user_service.exception.user.UserNotFoundException;
import com.booking.platform.user_service.repository.UserAttributeRepository;
import com.booking.platform.user_service.repository.UserRepository;
import com.booking.platform.user_service.service.DatabaseUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements DatabaseUserService<UserEntity, UserAttributeEntity> {

    private final UserRepository userRepository;
    private final UserAttributeRepository userAttributeRepository;

    @Cacheable(value = CacheConfig.CACHE_USER_BY_ID, key = "#a0")
    @Override
    public UserEntity getUserById(String userId) {
        log.debug("Fetching user by ID: {}", userId);

        return userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.forId(userId));
    }

    @Cacheable(value = CacheConfig.CACHE_USER_BY_USERNAME, key = "#a0")
    @Override
    public UserEntity getUserByUsername(String username) {
        log.debug("Fetching user by username: {}", username);

        return userRepository.findByUsername(username)
                .orElseThrow(() -> UserNotFoundException.forUsername(username));
    }

    @Cacheable(value = CacheConfig.CACHE_USER_BY_EMAIL, key = "#a0")
    @Override
    public UserEntity getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.forEmail(email));
    }

    @Override
    public List<UserEntity> getUsersByIds(List<String> userIds) {
        return List.of();
    }

    @Override
    public List<UserEntity> getAllUsers() {
        log.debug("Fetching all users");

        List<UserEntity> users = userRepository.findAll();
        log.debug("Retrieved {} users", users.size());

        return users;
    }

    @Override
    public Page<UserEntity> getAllUsers(Pageable pageable) {
        log.debug("Fetching users with pagination: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        return userRepository.findAll(pageable);
    }

    @Override
    public Page<UserEntity> searchUsers(String query, Pageable pageable) {
        log.debug("Searching users with query='{}', page={}, size={}", query, pageable.getPageNumber(), pageable.getPageSize());

        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }

        return userRepository.search(query.trim(), pageable);
    }

    @Override
    public long getUserCount() {
        long count = userRepository.count();
        log.debug("Total user count: {}", count);

        return count;
    }

    @Override
    public boolean existsByEmail(String email) {
        boolean exists = userRepository.existsByEmail(email);
        log.debug("User exists by email '{}': {}", email, exists);

        return exists;
    }

    @Override
    public boolean existsByUsername(String username) {
        boolean exists = userRepository.existsByUsername(username);
        log.debug("User exists by username '{}': {}", username, exists);

        return exists;
    }

    @Override
    public List<UserAttributeEntity> getUserAttributes(String userId) {
        log.debug("Fetching all attributes for user ID: {}", userId);

        return userAttributeRepository.findByUserId(userId);
    }

    @Override
    public List<UserAttributeEntity> getUserAttribute(String userId, String attributeName) {
        log.debug("Fetching attribute '{}' for user ID: {}", attributeName, userId);

        return userAttributeRepository.findByUserIdAndName(userId, attributeName);
    }

}

package com.booking.platform.user_service.repository;

import com.booking.platform.user_service.entity.UserAttributeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository for user attributes.
 */
@Repository
public interface UserAttributeRepository extends JpaRepository<UserAttributeEntity, String> {

    List<UserAttributeEntity> findByUserId(String userId);

    List<UserAttributeEntity> findByUserIdAndName(String userId, String name);
}

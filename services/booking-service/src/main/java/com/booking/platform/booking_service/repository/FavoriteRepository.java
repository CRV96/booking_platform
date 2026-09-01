package com.booking.platform.booking_service.repository;

import com.booking.platform.booking_service.entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FavoriteEntity}.
 *
 * <p>Every query is scoped by {@code userId}. Schema is managed by Flyway.
 */
public interface FavoriteRepository extends JpaRepository<FavoriteEntity, UUID> {

    /** A user's whole lovelist, most recently added first. */
    List<FavoriteEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /** The favorite for an event, if the user has one — the idempotency key for "add favorite". */
    Optional<FavoriteEntity> findByUserIdAndEventId(String userId, String eventId);

    /** Removes a user's favorite for an event; returns the number of rows deleted (0 = no-op). */
    long deleteByUserIdAndEventId(String userId, String eventId);
}

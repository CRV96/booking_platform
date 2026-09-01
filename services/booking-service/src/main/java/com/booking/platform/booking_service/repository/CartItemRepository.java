package com.booking.platform.booking_service.repository;

import com.booking.platform.booking_service.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CartItemEntity}.
 *
 * <p>Every query is scoped by {@code userId} so a user can only ever touch their own cart.
 * Schema is managed by Flyway.
 */
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    /** All lines in a user's cart, oldest first. */
    List<CartItemEntity> findByUserIdOrderByCreatedAtAsc(String userId);

    /** The existing line for an event + seat category, if any — the upsert key for "add to cart". */
    Optional<CartItemEntity> findByUserIdAndEventIdAndSeatCategory(String userId, String eventId, String seatCategory);

    /** A single line, but only if it belongs to the given user (ownership guard). */
    Optional<CartItemEntity> findByIdAndUserId(UUID id, String userId);

    /** Removes one line if it belongs to the user; returns the number of rows deleted (0 = no-op). */
    long deleteByIdAndUserId(UUID id, String userId);

    /** Empties a user's cart; returns the number of rows deleted. */
    long deleteByUserId(String userId);
}

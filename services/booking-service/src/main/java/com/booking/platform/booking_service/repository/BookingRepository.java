package com.booking.platform.booking_service.repository;

import com.booking.platform.booking_service.entity.BookingEntity;
import com.booking.platform.booking_service.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BookingEntity}.
 *
 * <p>All query methods are backed by the {@code bookings} table, schema managed by Flyway.
 * Paginated queries use {@link Page} to support the GraphQL pagination pattern used in
 * {@code graphql-gateway}.
 */
public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    /**
     * Returns all bookings for a given user, ordered by the database default.
     * Use this when no status filter is required.
     */
    Page<BookingEntity> findByUserId(String userId, Pageable pageable);

    /**
     * Returns bookings for a user filtered by a specific status.
     * Used when the GraphQL query includes a {@code statusFilter} argument.
     */
    Page<BookingEntity> findByUserIdAndStatus(String userId, BookingStatus status, Pageable pageable);

    /**
     * Returns all bookings for a given event.
     * Used by event-service integration when an event is cancelled — to trigger
     * compensating cancellations on all active bookings.
     */
    List<BookingEntity> findByEventId(String eventId);

    /**
     * Fetches a booking by ID only if it belongs to the given user.
     * Prevents users from reading other users' bookings without an extra service-layer check.
     */
    Optional<BookingEntity> findByIdAndUserId(UUID id, String userId);

    /**
     * Idempotency check — returns the existing booking for a given key if one exists.
     * Callers should return the existing booking instead of creating a duplicate.
     */
    Optional<BookingEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all PENDING bookings whose hold timer has expired.
     * Called by the P3-04 hold-expiration scheduler to auto-cancel stale bookings
     * and release the seat reservation.
     *
     * @param now the current time; all PENDING bookings with holdExpiresAt before this value are returned
     */
    @Query("SELECT b FROM BookingEntity b WHERE b.status = 'PENDING' AND b.holdExpiresAt < :now")
    List<BookingEntity> findExpiredHolds(@Param("now") Instant now);
}

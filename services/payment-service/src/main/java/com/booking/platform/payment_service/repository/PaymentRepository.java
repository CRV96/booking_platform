package com.booking.platform.payment_service.repository;

import com.booking.platform.payment_service.entity.PaymentEntity;
import com.booking.platform.payment_service.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PaymentEntity}.
 *
 * <p>All query methods are backed by the {@code payments} table, schema managed by Flyway.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    /**
     * Finds the payment associated with a specific booking.
     * Used by the saga flow to check payment status for a booking.
     */
    Optional<PaymentEntity> findByBookingId(String bookingId);

    /**
     * Idempotency check — returns the existing payment for a given key if one exists.
     * Prevents duplicate charges from retried Kafka messages.
     */
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds a payment by its external gateway ID (e.g. Stripe charge ID).
     * Used for webhook callbacks where the gateway sends its own ID, not ours.
     * Prepared for P4-02 (Stripe integration).
     */
    Optional<PaymentEntity> findByExternalPaymentId(String externalPaymentId);

    /**
     * Returns all PENDING_RETRY payments whose next retry time has passed.
     * Called by {@link com.booking.platform.payment_service.scheduler.PaymentRetryScheduler}
     * on each scheduler tick to find payments due for a retry attempt.
     */
    List<PaymentEntity> findByStatusAndNextRetryAtBefore(PaymentStatus status, Instant cutoff);
}

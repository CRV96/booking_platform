package com.booking.platform.payment_service.repository;

import com.booking.platform.payment_service.entity.OutboxEventEntity;
import com.booking.platform.payment_service.messaging.publisher.OutboxPollingPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEventEntity}.
 *
 * <p>Used by the outbox poller ({@link OutboxPollingPublisher})
 * to read unpublished events and clean up old published ones.
 *
 * <p>The queries leverage partial indexes defined in {@code V2__create_outbox_events_table.sql}:
 * <ul>
 *   <li>{@code idx_outbox_unpublished} — covers the {@link #findByPublishedAtIsNullOrderByCreatedAtAsc()} query</li>
 *   <li>{@code idx_outbox_published_cleanup} — covers the {@link #deleteByPublishedAtBefore(Instant)} query</li>
 * </ul>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Reads unpublished outbox events in FIFO order (oldest first), up to {@code pageable} limit.
     * The poller calls this every 500ms to find events that need to be published to Kafka.
     * The batch size cap prevents loading unbounded rows under backlog conditions.
     *
     * <p>Uses the partial index {@code idx_outbox_unpublished} for efficient filtering.
     */
    List<OutboxEventEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Deletes published outbox events older than the given cutoff.
     * The cleanup job runs hourly and removes events published more than 24 hours ago.
     *
     * <p>Uses the partial index {@code idx_outbox_published_cleanup} for efficient deletion.
     */
    @Modifying
    void deleteByPublishedAtBefore(Instant cutoff);
}

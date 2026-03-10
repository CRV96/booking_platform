package com.booking.platform.analytics_service.document;

import com.booking.platform.analytics_service.constants.BkgAnalyticsConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable raw event log — every Kafka event consumed by analytics-service
 * is stored here as-is for auditing and replay.
 *
 * <p>This is the "event store" side of CQRS. Events are never updated or deleted
 * (except by TTL expiry after 90 days).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(BkgAnalyticsConstants.BkgDocumentConstants.EVENT_LOG_COLLECTION)
public class EventLog {

    @Id
    private String id;

    /** Proto message type name (e.g. "EventCreatedEvent", "BookingConfirmedEvent"). */
    private String eventType;

    /** Kafka topic the event was consumed from. */
    private String topic;

    /** Kafka message key (typically the entity ID). */
    private String key;

    /** Event payload as a flat map of field names to values. */
    private Map<String, Object> payload;

    /** When this event was received by analytics-service. */
    private Instant receivedAt;
}

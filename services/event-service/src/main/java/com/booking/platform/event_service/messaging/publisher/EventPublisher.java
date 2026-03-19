package com.booking.platform.event_service.messaging.publisher;

import com.booking.platform.event_service.document.EventDocument;

import java.util.List;

public interface EventPublisher {
    /** Publishes an EVENT_CREATED event after a new event is persisted in DRAFT status. */
    void publishEventCreated(EventDocument event);
    /** Publishes an EVENT_UPDATED event listing the changed field names. */
    void publishEventUpdated(EventDocument event, List<String> changedFields);
    /** Publishes an EVENT_PUBLISHED event after DRAFT → PUBLISHED transition. */
    void publishEventPublished(EventDocument event);
    /** Publishes an EVENT_CANCELLED event with the cancellation reason. */
    void publishEventCancelled(EventDocument event, String reason);
}

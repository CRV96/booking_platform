package com.booking.platform.event_service.messaging.publisher;

import com.booking.platform.event_service.document.EventDocument;

import java.util.List;

public interface EventPublisher {
    void publishEventCreated(EventDocument event);
    void publishEventUpdated(EventDocument event, List<String> changedFields);
    void publishEventPublished(EventDocument event);
    void publishEventCancelled(EventDocument event, String reason);
}

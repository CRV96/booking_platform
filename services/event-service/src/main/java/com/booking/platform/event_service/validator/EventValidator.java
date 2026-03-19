package com.booking.platform.event_service.validator;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.event_service.document.EventDocument;

public interface EventValidator {

    /** Validates fields required to create a new event (title, category, dateTime, seats). */
    void validateCreateRequest(CreateEventRequest request);

    /** Validates that an event is ready to be published (venue, dateTime, seats present and valid). */
    void validateForPublish(EventDocument event);
}

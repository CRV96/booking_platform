package com.booking.platform.event_service.validator;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.event_service.document.EventDocument;

import java.time.Instant;

/**
 * Validator interface for event creation and publication rules. Implementations will check required fields and business logic before allowing events to be created or published.
 */
public interface EventValidator {

    /** Validates fields required to create a new event (title, category, dateTime, seats). */
    void validateCreateRequest(CreateEventRequest request);

    /** Validates that an event is ready to be published (venue, dateTime, seats present and valid). */
    void validateForPublish(EventDocument event);

    /**
     * Parses a string into an Instant, throwing a ValidationException with a clear message if the format is invalid. Used to validate dateTime fields in requests.
     */
    Instant parseInstant(String value, String fieldName);
}

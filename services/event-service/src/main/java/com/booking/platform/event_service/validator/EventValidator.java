package com.booking.platform.event_service.validator;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.event_service.document.EventDocument;

public interface EventValidator {

    void validateCreateRequest(CreateEventRequest request);

    void validateForPublish(EventDocument event);
}

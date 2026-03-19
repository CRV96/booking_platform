package com.booking.platform.event_service.validator.impl;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.exception.ValidationException;
import com.booking.platform.event_service.validator.EventValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class EventValidatorImpl implements EventValidator {

    @Override
    public void validateCreateRequest(CreateEventRequest request) {
        if (request.getTitle().isBlank()) {
            throw new ValidationException("Event title must not be blank");
        }
        if (request.getCategory().isBlank()) {
            throw new ValidationException("Event category must not be blank");
        }
        if (request.getDateTime().isBlank()) {
            throw new ValidationException("Event dateTime must not be blank");
        }
        try {
            EventCategory.valueOf(request.getCategory());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid event category: " + request.getCategory());
        }
        if (request.getSeatCategoriesList().isEmpty()) {
            throw new ValidationException("Event must have at least one seat category");
        }
        request.getSeatCategoriesList().forEach(sc -> {
            if (sc.getName().isBlank()) {
                throw new ValidationException("Seat category name must not be blank");
            }
            if (sc.getPrice() < 0) {
                throw new ValidationException("Seat category price must not be negative: " + sc.getName());
            }
            if (sc.getTotalSeats() <= 0) {
                throw new ValidationException("Seat category totalSeats must be positive: " + sc.getName());
            }
            if (sc.getCurrency().isBlank()) {
                throw new ValidationException("Seat category currency must not be blank: " + sc.getName());
            }
        });
    }

    @Override
    public void validateForPublish(EventDocument event) {
        if (event.getTitle() == null || event.getTitle().isBlank()) {
            throw new ValidationException("Cannot publish event: title is missing");
        }
        if (event.getVenue() == null || event.getVenue().getName().isBlank()) {
            throw new ValidationException("Cannot publish event: venue is missing");
        }
        if (event.getDateTime() == null) {
            throw new ValidationException("Cannot publish event: dateTime is missing");
        }
        if (event.getDateTime().isBefore(Instant.now())) {
            throw new ValidationException("Cannot publish event: dateTime is in the past");
        }
        if (event.getSeatCategories() == null || event.getSeatCategories().isEmpty()) {
            throw new ValidationException("Cannot publish event: must have at least one seat category");
        }
    }
}

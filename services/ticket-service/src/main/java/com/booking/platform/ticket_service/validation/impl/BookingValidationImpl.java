package com.booking.platform.ticket_service.validation.impl;

import com.booking.platform.ticket_service.dto.TicketDTO;
import com.booking.platform.ticket_service.exception.InvalidTicketOperationException;
import com.booking.platform.ticket_service.properties.TicketProperties;
import com.booking.platform.ticket_service.validation.BookingValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class BookingValidationImpl implements BookingValidation {

    private final TicketProperties ticketProperties;

    @Override
    public void validateTicketRequest(TicketDTO source) {
        if (source == null) {
            throw new InvalidTicketOperationException("Ticket request cannot be null");
        }
        validateRequired(source.bookingId(), "bookingId");
        validateRequired(source.eventId(), "eventId");
        validateRequired(source.userId(), "userId");
        validateQuantity(source.quantity());
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            log.warn("Validation failed: {} is required", fieldName);
            throw new InvalidTicketOperationException(fieldName + " is required");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0 || quantity > ticketProperties.maxQuantityPerBooking()) {
            log.warn("Invalid booking quantity: {}", quantity);
            throw new InvalidTicketOperationException(
                    "Booking quantity must be between 1 and " + ticketProperties.maxQuantityPerBooking());
        }
    }
}

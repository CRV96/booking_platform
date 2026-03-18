package com.booking.platform.ticket_service.validation;

import com.booking.platform.ticket_service.dto.TicketDTO;

public interface BookingValidation {
    void validateTicketRequest(TicketDTO source);
}

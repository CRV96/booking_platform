package com.booking.platform.booking_service.grpc.client;

import com.booking.platform.common.grpc.event.EventResponse;
import com.booking.platform.common.grpc.event.UpdateSeatAvailabilityResponse;

/**
 * Client interface for communicating with event-service via gRPC.
 * Used by booking-service to validate events and manage seat availability.
 */
public interface EventServiceClient {

    /** Fetch event details (pricing, seat categories, status). */
    EventResponse getEvent(String eventId);

    /** Atomically decrement or increment seat availability. */
    UpdateSeatAvailabilityResponse updateSeatAvailability(
            String eventId, String seatCategoryName, int delta);
}

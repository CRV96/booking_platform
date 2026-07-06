package com.booking.platform.booking_service.grpc.client.impl;

import com.booking.platform.booking_service.grpc.client.EventServiceClient;
import com.booking.platform.common.grpc.event.*;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * gRPC client implementation for calling event-service.
 * Uses mTLS when {@code grpc.client.security.enabled=true}.
 */
@Slf4j
@Service
public class EventServiceClientImpl implements EventServiceClient {

    @GrpcClient("event-service")
    private EventServiceGrpc.EventServiceBlockingStub eventServiceStub;

    @Override
    public EventResponse getEvent(String eventId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling event-service: GetEvent '{}'", eventId);
        return eventServiceStub.getEvent(
                GetEventRequest.newBuilder()
                        .setEventId(eventId)
                        .build()
        );
    }

    @Override
    public UpdateSeatAvailabilityResponse updateSeatAvailability(
            String eventId, String seatCategoryName, int delta) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling event-service: UpdateSeatAvailability event='{}', category='{}', delta={}",
                eventId, seatCategoryName, delta);
        return eventServiceStub.updateSeatAvailability(
                UpdateSeatAvailabilityRequest.newBuilder()
                        .setEventId(eventId)
                        .setSeatCategoryName(seatCategoryName)
                        .setDelta(delta)
                        .build()
        );
    }
}

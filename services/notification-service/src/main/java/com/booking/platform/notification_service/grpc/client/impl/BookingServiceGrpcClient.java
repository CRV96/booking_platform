package com.booking.platform.notification_service.grpc.client.impl;

import com.booking.platform.common.grpc.booking.BookingServiceGrpc;
import com.booking.platform.common.grpc.booking.GetBookingAttendeesRequest;
import com.booking.platform.common.grpc.booking.GetBookingAttendeesResponse;
import com.booking.platform.notification_service.grpc.client.BookingServiceClient;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class BookingServiceGrpcClient implements BookingServiceClient {

    @GrpcClient("booking-service")
    private BookingServiceGrpc.BookingServiceBlockingStub bookingServiceStub;

    @Override
    public List<String> getBookingAttendees(String eventId, String eventStatus) {
        GetBookingAttendeesResponse getBookingAttendeesResponse =
                bookingServiceStub.getBookingAttendees(GetBookingAttendeesRequest.newBuilder().setEventId(eventId).setEventStatus(eventStatus).build());

        ApplicationLogger.logMessage(log, Level.DEBUG, "Received attendees for event '{}': {}", eventId, getBookingAttendeesResponse.getAttendeesList());

        return getBookingAttendeesResponse.getAttendeesList();
    }
}

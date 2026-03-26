package com.booking.platform.notification_service.grpc.client;


import java.util.List;

public interface BookingServiceClient {

    // Method to get the list of attendees for a given booking using eventId
    List<String> getBookingAttendees(String eventId, String eventStatus);
}

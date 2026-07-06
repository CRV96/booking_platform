package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.booking.BookingResponse;
import com.booking.platform.common.grpc.booking.GetUserBookingsResponse;

/**
 * Client interface for communicating with the booking-service via gRPC.
 */
public interface BookingClient {

    BookingResponse createBooking(String eventId, String seatCategory,
                                  int quantity, String idempotencyKey);

    BookingResponse getBooking(String bookingId);

    GetUserBookingsResponse getUserBookings(String userId, int page,
                                            int pageSize, String statusFilter);

    BookingResponse cancelBooking(String bookingId, String reason);
}

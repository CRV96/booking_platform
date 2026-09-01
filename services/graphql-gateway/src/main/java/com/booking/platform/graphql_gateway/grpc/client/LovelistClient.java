package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.booking.LoveListResponse;

/**
 * Client interface for the lovelist endpoints on booking-service (gRPC).
 * The JWT is forwarded automatically, so the user is resolved downstream.
 */
public interface LovelistClient {

    LoveListResponse getLoveList();

    LoveListResponse addFavorite(String eventId);

    LoveListResponse removeFavorite(String eventId);
}

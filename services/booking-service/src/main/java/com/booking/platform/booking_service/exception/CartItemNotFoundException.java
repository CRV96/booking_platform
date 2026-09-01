package com.booking.platform.booking_service.exception;

import io.grpc.Status;

/**
 * Thrown when a cart line cannot be found for the given id and user.
 */
public class CartItemNotFoundException extends BookingServiceException {

    public CartItemNotFoundException(String cartItemId) {
        super("Cart item not found: " + cartItemId);
    }

    @Override
    public Status.Code getGrpcStatusCode() {
        return Status.Code.NOT_FOUND;
    }
}

package com.booking.platform.booking_service.exception;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemNotFoundExceptionTest {

    @Test
    void carriesIdInMessageAndMapsToNotFound() {
        CartItemNotFoundException ex = new CartItemNotFoundException("item-123");

        assertThat(ex.getMessage()).contains("item-123");
        assertThat(ex.getGrpcStatusCode()).isEqualTo(Status.Code.NOT_FOUND);
    }
}

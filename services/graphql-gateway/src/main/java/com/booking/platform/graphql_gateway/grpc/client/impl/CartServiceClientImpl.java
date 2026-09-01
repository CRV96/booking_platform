package com.booking.platform.graphql_gateway.grpc.client.impl;

import com.booking.platform.common.grpc.booking.*;
import com.booking.platform.common.logging.ApplicationLogger;
import com.booking.platform.graphql_gateway.constants.BookingServiceConst;
import com.booking.platform.graphql_gateway.dto.cart.AddToCartInput;
import com.booking.platform.graphql_gateway.grpc.client.CartClient;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;

/**
 * gRPC client for the cart endpoints on booking-service.
 * JWT is forwarded automatically by {@code JwtForwardingClientInterceptor}.
 */
@Service
@Slf4j
public class CartServiceClientImpl implements CartClient {

    @GrpcClient(BookingServiceConst.GRPC_CLIENT)
    private CartServiceGrpc.CartServiceBlockingStub cartStub;

    @Override
    public CartResponse getCart() {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: GetCart");
        return cartStub.getCart(GetCartRequest.getDefaultInstance());
    }

    @Override
    public CartResponse addToCart(AddToCartInput input) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: AddToCart event='{}', category='{}', qty={}",
                input.eventId(), input.seatCategory(), input.quantity());

        return cartStub.addToCart(AddToCartRequest.newBuilder()
                .setEventId(input.eventId())
                .setEventTitle(input.eventTitle())
                .setSeatCategory(input.seatCategory())
                .setQuantity(input.quantity())
                .setUnitPrice(input.unitPrice())
                .setCurrency(input.currency())
                .build());
    }

    @Override
    public CartResponse updateCartItem(String cartItemId, int quantity) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: UpdateCartItem item='{}', qty={}", cartItemId, quantity);

        return cartStub.updateCartItem(UpdateCartItemRequest.newBuilder()
                .setCartItemId(cartItemId)
                .setQuantity(quantity)
                .build());
    }

    @Override
    public CartResponse removeFromCart(String cartItemId) {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: RemoveFromCart item='{}'", cartItemId);

        return cartStub.removeFromCart(RemoveFromCartRequest.newBuilder()
                .setCartItemId(cartItemId)
                .build());
    }

    @Override
    public CartResponse clearCart() {
        ApplicationLogger.logMessage(log, Level.DEBUG, "Calling booking-service: ClearCart");
        return cartStub.clearCart(ClearCartRequest.getDefaultInstance());
    }
}

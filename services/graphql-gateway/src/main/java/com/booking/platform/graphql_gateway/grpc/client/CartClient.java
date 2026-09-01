package com.booking.platform.graphql_gateway.grpc.client;

import com.booking.platform.common.grpc.booking.CartResponse;
import com.booking.platform.graphql_gateway.dto.cart.AddToCartInput;

/**
 * Client interface for the cart endpoints on booking-service (gRPC).
 * The JWT is forwarded automatically, so the user is resolved downstream.
 */
public interface CartClient {

    CartResponse getCart();

    CartResponse addToCart(AddToCartInput input);

    CartResponse updateCartItem(String cartItemId, int quantity);

    CartResponse removeFromCart(String cartItemId);

    CartResponse clearCart();
}

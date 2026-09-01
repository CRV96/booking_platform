package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.CartItemEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Per-user shopping cart. Every operation is scoped to a {@code userId} (resolved from
 * the JWT at the gRPC boundary), so a caller can only ever read or mutate their own cart.
 */
public interface CartService {

    /** Returns the user's cart lines, oldest first. */
    List<CartItemEntity> getCart(String userId);

    /**
     * Adds a line to the cart, or updates the quantity/snapshot if the same
     * {@code (event, seatCategory)} line already exists (upsert on the unique key).
     * Idempotent under retries.
     */
    CartItemEntity addItem(String userId, String eventId, String eventTitle,
                           String seatCategory, int quantity, BigDecimal unitPrice, String currency);

    /**
     * Sets the quantity of an existing line the user owns.
     *
     * @throws com.booking.platform.booking_service.exception.CartItemNotFoundException
     *         if the line does not exist for this user
     */
    CartItemEntity updateItemQuantity(String userId, UUID cartItemId, int quantity);

    /** Removes a line the user owns. No-op if it does not exist. */
    void removeItem(String userId, UUID cartItemId);

    /** Empties the user's cart. */
    void clearCart(String userId);
}

package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.FavoriteEntity;

import java.util.List;

/**
 * Per-user "lovelist" (favorites). Every operation is scoped to a {@code userId}
 * (resolved from the JWT at the gRPC boundary).
 */
public interface LovelistService {

    /** Returns the user's favorites, most recently added first. */
    List<FavoriteEntity> getFavorites(String userId);

    /**
     * Adds an event to the user's lovelist, or returns the existing favorite if it is
     * already there. Idempotent under retries and concurrent double-adds.
     */
    FavoriteEntity addFavorite(String userId, String eventId);

    /** Removes an event from the user's lovelist. No-op if it was not favorited. */
    void removeFavorite(String userId, String eventId);
}

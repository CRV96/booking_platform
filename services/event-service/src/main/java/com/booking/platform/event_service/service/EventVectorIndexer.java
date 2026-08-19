package com.booking.platform.event_service.service;

/**
 * Keeps the semantic-search vector store ({@code event_vectors}) in sync with events.
 *
 * <p>Invoked asynchronously from Kafka lifecycle events (see
 * {@code VectorIndexConsumer}) so the OpenAI embedding call stays off the event
 * write path and gains Kafka's retry + dead-letter guarantees.
 */
public interface EventVectorIndexer {

    /**
     * Embeds the current state of the event (re-fetched from Mongo) and upserts its
     * vector. If the event no longer exists, any stale vector is removed instead.
     */
    void index(String eventId);

    /** Removes the event's vector from the store (e.g. on cancellation). Idempotent. */
    void remove(String eventId);
}

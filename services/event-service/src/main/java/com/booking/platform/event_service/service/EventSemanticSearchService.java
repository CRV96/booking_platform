package com.booking.platform.event_service.service;

import com.booking.platform.event_service.document.EventDocument;

import java.util.List;

/**
 * Natural-language ("semantic") search over events, backed by the vector store.
 *
 * <p>Unlike the keyword search on {@link EventService}, this matches by <em>meaning</em>:
 * the query is embedded into the same vector space as the events and the nearest ones
 * are returned, ranked by similarity.
 */
public interface EventSemanticSearchService {

    /**
     * Returns up to {@code topK} published events most semantically similar to the query,
     * best match first. Optional {@code category}/{@code city} apply the same hard filters
     * as the keyword search (null or blank = no filter on that field).
     */
    List<EventDocument> search(String query, int topK, String category, String city);
}

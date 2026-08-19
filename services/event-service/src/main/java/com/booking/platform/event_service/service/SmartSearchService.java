package com.booking.platform.event_service.service;

import com.booking.platform.common.grpc.event.SearchEventsRequest;

/**
 * Coordinates the two searches behind the "AI Search" toggle: always runs the classic
 * keyword search, and — when {@code aiSearch} is on and the feature is enabled — adds an
 * additive set of semantic "smart results".
 */
public interface SmartSearchService {

    /**
     * @param request  the same request the keyword search uses (query, category, city, paging)
     * @param aiSearch whether the "AI Search" toggle is on; when false, smartResults is empty
     */
    SmartSearchResult search(SearchEventsRequest request, boolean aiSearch);
}

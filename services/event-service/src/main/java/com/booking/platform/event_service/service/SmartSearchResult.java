package com.booking.platform.event_service.service;

import com.booking.platform.event_service.document.EventDocument;

import java.util.List;

/**
 * Result of a combined search: classic keyword {@code results} plus, when AI Search is
 * on, {@code smartResults} — semantic matches the keyword search did NOT already return
 * (additive; no event appears in both lists).
 */
public record SmartSearchResult(
        List<EventDocument> results,
        List<EventDocument> smartResults
) {}

package com.booking.platform.event_service.service.impl;

import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.service.EventSemanticSearchService;
import com.booking.platform.event_service.service.EventService;
import com.booking.platform.event_service.service.SmartSearchResult;
import com.booking.platform.event_service.service.SmartSearchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link SmartSearchService}.
 *
 * <p>The semantic service is an <em>optional</em> dependency (injected via
 * {@link ObjectProvider}) because it only exists when {@code app.semantic-search.enabled=true}.
 * With the feature off, this coordinator still serves classic results and simply returns
 * empty smart results — so the search endpoint keeps working either way.
 */
@Slf4j
@Service
public class SmartSearchServiceImpl implements SmartSearchService {

    private final EventService eventService;
    private final ObjectProvider<EventSemanticSearchService> semanticSearchProvider;
    private final MeterRegistry meterRegistry;

    /** How many smart results to return after removing overlaps with the keyword results. */
    private final int smartResultsLimit;

    public SmartSearchServiceImpl(
            EventService eventService,
            ObjectProvider<EventSemanticSearchService> semanticSearchProvider,
            MeterRegistry meterRegistry,
            @Value("${app.semantic-search.smart-results-limit:10}") int smartResultsLimit) {
        this.eventService = eventService;
        this.semanticSearchProvider = semanticSearchProvider;
        this.meterRegistry = meterRegistry;
        this.smartResultsLimit = smartResultsLimit;
    }

    @Override
    public SmartSearchResult search(SearchEventsRequest request, boolean aiSearch) {
        // Classic keyword results always run.
        List<EventDocument> classic = eventService.searchEvents(request);

        EventSemanticSearchService semantic = semanticSearchProvider.getIfAvailable();
        if (!aiSearch || semantic == null) {
            // Toggle off, or feature disabled → classic only.
            return new SmartSearchResult(classic, List.of());
        }

        return new SmartSearchResult(classic, smartResults(request, classic, semantic));
    }

    /**
     * Additive smart results, guarded. If the embedding provider is down/slow the whole
     * search must NOT fail — we log, count a fallback, and return empty smart results so
     * the caller still gets the classic keyword results. Latency/outcome are metered.
     */
    private List<EventDocument> smartResults(SearchEventsRequest request,
                                             List<EventDocument> classic,
                                             EventSemanticSearchService semantic) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Set<String> classicIds = classic.stream().map(EventDocument::getId).collect(Collectors.toSet());
            // Over-fetch so that, after dropping overlaps, we can still fill smartResultsLimit.
            int fetch = smartResultsLimit + classicIds.size();

            List<EventDocument> smart = semantic.search(
                            request.getQuery(),
                            fetch,
                            emptyToNull(request.getCategory()),
                            emptyToNull(request.getCity()))
                    .stream()
                    .filter(event -> !classicIds.contains(event.getId()))
                    .limit(smartResultsLimit)
                    .toList();

            sample.stop(meterRegistry.timer("event.semantic_search", "outcome", "success"));
            log.debug("Smart search '{}' → {} classic, {} smart (additive)",
                    request.getQuery(), classic.size(), smart.size());
            return smart;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("event.semantic_search", "outcome", "error"));
            meterRegistry.counter("event.semantic_search.fallback").increment();
            log.warn("Semantic search failed for query '{}' — returning classic results only: {}",
                    request.getQuery(), e.getMessage());
            return List.of();
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}

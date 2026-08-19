package com.booking.platform.event_service.service.impl;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventSemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vector-backed semantic search. Embeds the query (via the configured EmbeddingModel),
 * asks the {@link VectorStore} for the nearest event vectors, then hydrates the full
 * {@link EventDocument}s from Mongo — the vector store ranks, Mongo remains the source
 * of truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class EventSemanticSearchServiceImpl implements EventSemanticSearchService {

    private final VectorStore vectorStore;
    private final EventRepository eventRepository;

    @Override
    public List<EventDocument> search(String query, int topK, String category, String city) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(buildFilter(category, city))
                .build();

        // 1) Vector store returns the nearest Documents, ranked best-first. Each id = eventId.
        List<Document> hits = vectorStore.similaritySearch(request);
        List<String> rankedIds = hits.stream().map(Document::getId).toList();
        if (rankedIds.isEmpty()) {
            return List.of();
        }

        // 2) Hydrate the full events from Mongo (unordered), then restore the ranking.
        Map<String, EventDocument> byId = eventRepository.findAllById(rankedIds).stream()
                .collect(Collectors.toMap(EventDocument::getId, Function.identity()));

        List<EventDocument> results = rankedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        log.debug("Semantic search '{}' (category={}, city={}) → {} hits, {} hydrated",
                query, category, city, rankedIds.size(), results.size());
        return results;
    }

    /**
     * Always restrict to published events; add category/city as extra AND filters when
     * provided. Built programmatically (not string concatenation) so values with quotes
     * or apostrophes (e.g. a city name) can't break the expression.
     */
    private Filter.Expression buildFilter(String category, String city) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expr = b.eq(DocumentConst.VectorStore.META_STATUS, EventStatus.PUBLISHED.name());
        if (StringUtils.hasText(category)) {
            expr = b.and(expr, b.eq(DocumentConst.VectorStore.META_CATEGORY, category));
        }
        if (StringUtils.hasText(city)) {
            expr = b.and(expr, b.eq(DocumentConst.VectorStore.META_CITY, city));
        }
        return expr.build();
    }
}

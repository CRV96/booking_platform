package com.booking.platform.event_service.service.impl;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventSemanticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
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
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class EventSemanticSearchServiceImpl implements EventSemanticSearchService {

    private final VectorStore vectorStore;
    private final EventRepository eventRepository;

    /**
     * Minimum similarity (MongoDB Atlas cosine score = (1 + cosine) / 2, so 0.5 = unrelated,
     * 1.0 = identical) for a result to count. Drops loosely-related "noise" so smart results
     * only appear when genuinely relevant. Tune per the score distribution of your data.
     */
    private final double similarityThreshold;

    public EventSemanticSearchServiceImpl(
            VectorStore vectorStore,
            EventRepository eventRepository,
            @Value("${app.semantic-search.similarity-threshold:0.78}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.eventRepository = eventRepository;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<EventDocument> search(String query, int topK, String category, String city) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(buildFilter(category, city))
                .build();

        // 1) Nearest Documents, ranked best-first. Belt-and-suspenders: also drop anything
        //    below the threshold by score in case the store didn't pre-filter. Strip the id
        //    prefix to recover the eventId (see DocumentConst.VectorStore.ID_PREFIX).
        List<Document> hits = vectorStore.similaritySearch(request);
        List<String> rankedIds = hits.stream()
                .filter(hit -> hit.getScore() == null || hit.getScore() >= similarityThreshold)
                .map(hit -> stripIdPrefix(hit.getId()))
                .toList();
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
    private String stripIdPrefix(String vectorId) {
        String prefix = DocumentConst.VectorStore.ID_PREFIX;
        return vectorId.startsWith(prefix) ? vectorId.substring(prefix.length()) : vectorId;
    }

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

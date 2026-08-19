package com.booking.platform.event_service.service.impl;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventVectorIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link EventVectorIndexer}: turns an {@link EventDocument} into a Spring AI
 * {@link Document} (embeddable text + filterable metadata) and upserts/removes it in
 * the {@link VectorStore}. The {@code vectorStore.add(...)} call is what triggers the
 * OpenAI embedding request under the hood.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventVectorIndexerImpl implements EventVectorIndexer {

    private final EventRepository eventRepository;
    private final VectorStore vectorStore;

    @Override
    public void index(String eventId) {
        EventDocument event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Event '{}' not found in Mongo — removing any stale vector instead of indexing", eventId);
            remove(eventId);
            return;
        }

        // Upsert: delete the previous vector (if any) for this id, then add the fresh one.
        // Using the eventId as the Document id keeps a single entry per event.
        vectorStore.delete(List.of(eventId));
        vectorStore.add(List.of(toVectorDocument(event)));
        log.info("Indexed event '{}' into vector store '{}'", eventId, DocumentConst.VectorStore.COLLECTION_NAME);
    }

    @Override
    public void remove(String eventId) {
        vectorStore.delete(List.of(eventId));
        log.info("Removed event '{}' from vector store '{}'", eventId, DocumentConst.VectorStore.COLLECTION_NAME);
    }

    /**
     * Builds the Spring AI {@link Document}: id = eventId (so search results carry it
     * back for hydration from Mongo), text = the blob we embed, metadata = the fields
     * declared filterable on the vectorSearch index.
     */
    private Document toVectorDocument(EventDocument event) {
        Map<String, Object> metadata = new HashMap<>();
        putIfNotNull(metadata, DocumentConst.VectorStore.META_CATEGORY,
                event.getCategory() != null ? event.getCategory().name() : null);
        putIfNotNull(metadata, DocumentConst.VectorStore.META_CITY,
                event.getVenue() != null ? event.getVenue().getCity() : null);
        putIfNotNull(metadata, DocumentConst.VectorStore.META_STATUS,
                event.getStatus() != null ? event.getStatus().name() : null);
        // Stored as epoch millis (a number) so the vectorSearch filter can do range queries.
        putIfNotNull(metadata, DocumentConst.VectorStore.META_DATE_TIME,
                event.getDateTime() != null ? event.getDateTime().toEpochMilli() : null);

        return new Document(event.getId(), buildEmbeddingText(event), metadata);
    }

    /**
     * Combines the human-meaningful fields into one text blob. This — not the raw
     * document — is what gets embedded, so word choice here shapes search quality.
     */
    private String buildEmbeddingText(EventDocument event) {
        List<String> parts = new ArrayList<>();
        addIfText(parts, event.getTitle());
        addIfText(parts, event.getDescription());
        if (event.getCategory() != null) {
            addIfText(parts, event.getCategory().name());
        }
        if (event.getTags() != null) {
            event.getTags().forEach(tag -> addIfText(parts, tag));
        }
        if (event.getVenue() != null) {
            addIfText(parts, event.getVenue().getName());
            addIfText(parts, event.getVenue().getCity());
        }
        return String.join("\n", parts);
    }

    private void addIfText(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value);
        }
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}

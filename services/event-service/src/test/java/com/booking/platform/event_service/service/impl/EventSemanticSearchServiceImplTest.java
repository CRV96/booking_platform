package com.booking.platform.event_service.service.impl;

import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventSemanticSearchServiceImplTest {

    private static final double THRESHOLD = 0.78;

    @Mock private VectorStore vectorStore;
    @Mock private EventRepository eventRepository;

    private EventSemanticSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EventSemanticSearchServiceImpl(vectorStore, eventRepository, THRESHOLD);
    }

    private Document hit(String id, double score) {
        return Document.builder().id(id).text("content").score(score).build();
    }

    private EventDocument event(String id) {
        return EventDocument.builder().id(id).title("title-" + id).build();
    }

    @Test
    void stripsIdPrefix_hydratesFromMongo_andPreservesRankOrder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("evt_2", 0.90), hit("evt_1", 0.85)));
        // Mongo returns them unordered; the service must restore the vector ranking.
        when(eventRepository.findAllById(List.of("2", "1")))
                .thenReturn(List.of(event("1"), event("2")));

        List<EventDocument> result = service.search("q", 10, null, null);

        assertThat(result).extracting(EventDocument::getId).containsExactly("2", "1");
    }

    @Test
    void dropsHitsBelowSimilarityThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("evt_1", 0.90), hit("evt_2", 0.50)));
        when(eventRepository.findAllById(List.of("1"))).thenReturn(List.of(event("1")));

        List<EventDocument> result = service.search("q", 10, null, null);

        assertThat(result).extracting(EventDocument::getId).containsExactly("1");
    }

    @Test
    void appliesCategoryAndCityFilters() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("evt_1", 0.90)));
        when(eventRepository.findAllById(List.of("1"))).thenReturn(List.of(event("1")));

        List<EventDocument> result = service.search("q", 10, "CONCERT", "Berlin");

        assertThat(result).extracting(EventDocument::getId).containsExactly("1");
    }

    @Test
    void returnsEmpty_whenAllHitsBelowThreshold_withoutHittingMongo() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("evt_1", 0.60), hit("evt_2", 0.50)));

        List<EventDocument> result = service.search("q", 10, null, null);

        assertThat(result).isEmpty();
        verify(eventRepository, never()).findAllById(anyList());
    }
}

package com.booking.platform.event_service.service.impl;

import com.booking.platform.common.grpc.event.SearchEventsRequest;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.service.EventSemanticSearchService;
import com.booking.platform.event_service.service.EventService;
import com.booking.platform.event_service.service.SmartSearchResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmartSearchServiceImplTest {

    @Mock private EventService eventService;
    @Mock private EventSemanticSearchService semanticSearch;
    @Mock private ObjectProvider<EventSemanticSearchService> semanticProvider;

    private SmartSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmartSearchServiceImpl(eventService, semanticProvider, new SimpleMeterRegistry(), 10);
    }

    private EventDocument ev(String id) {
        return EventDocument.builder().id(id).build();
    }

    private SearchEventsRequest req(String query, String category, String city) {
        SearchEventsRequest.Builder b = SearchEventsRequest.newBuilder().setQuery(query);
        if (category != null) b.setCategory(category);
        if (city != null) b.setCity(city);
        return b.build();
    }

    @Test
    void aiSearchOff_returnsClassicOnly_andNeverCallsSemantic() {
        when(eventService.searchEvents(any())).thenReturn(List.of(ev("1"), ev("2")));
        when(semanticProvider.getIfAvailable()).thenReturn(semanticSearch);

        SmartSearchResult result = service.search(req("q", null, null), false);

        assertThat(result.results()).extracting(EventDocument::getId).containsExactly("1", "2");
        assertThat(result.smartResults()).isEmpty();
        verify(semanticSearch, never()).search(any(), anyInt(), any(), any());
    }

    @Test
    void featureDisabled_semanticBeanAbsent_returnsClassicOnly() {
        when(eventService.searchEvents(any())).thenReturn(List.of(ev("1")));
        when(semanticProvider.getIfAvailable()).thenReturn(null);

        SmartSearchResult result = service.search(req("q", null, null), true);

        assertThat(result.results()).extracting(EventDocument::getId).containsExactly("1");
        assertThat(result.smartResults()).isEmpty();
    }

    @Test
    void aiSearchOn_additiveDedup_removesEventsAlreadyInClassic() {
        when(eventService.searchEvents(any())).thenReturn(List.of(ev("A"), ev("B")));
        when(semanticProvider.getIfAvailable()).thenReturn(semanticSearch);
        when(semanticSearch.search(eq("q"), anyInt(), any(), any()))
                .thenReturn(List.of(ev("B"), ev("C"), ev("D"))); // B overlaps classic

        SmartSearchResult result = service.search(req("q", null, null), true);

        assertThat(result.results()).extracting(EventDocument::getId).containsExactly("A", "B");
        assertThat(result.smartResults()).extracting(EventDocument::getId).containsExactly("C", "D");
    }

    @Test
    void aiSearchOn_semanticFails_fallsBackToClassicOnly() {
        when(eventService.searchEvents(any())).thenReturn(List.of(ev("1"), ev("2")));
        when(semanticProvider.getIfAvailable()).thenReturn(semanticSearch);
        when(semanticSearch.search(any(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("ollama unreachable"));

        SmartSearchResult result = service.search(req("q", null, null), true);

        // Search must not blow up — classic results still returned, smart empty.
        assertThat(result.results()).extracting(EventDocument::getId).containsExactly("1", "2");
        assertThat(result.smartResults()).isEmpty();
    }

    @Test
    void aiSearchOn_passesCategoryAndCity_blankBecomesNull() {
        when(eventService.searchEvents(any())).thenReturn(List.of());
        when(semanticProvider.getIfAvailable()).thenReturn(semanticSearch);
        when(semanticSearch.search(any(), anyInt(), any(), any())).thenReturn(List.of());

        service.search(req("jazz", "MUSIC", ""), true); // city blank → null

        verify(semanticSearch).search(eq("jazz"), anyInt(), eq("MUSIC"), isNull());
    }

    @Test
    void aiSearchOn_capsSmartResultsAtConfiguredLimit() {
        service = new SmartSearchServiceImpl(eventService, semanticProvider, new SimpleMeterRegistry(), 2); // limit 2
        when(eventService.searchEvents(any())).thenReturn(List.of());
        when(semanticProvider.getIfAvailable()).thenReturn(semanticSearch);
        when(semanticSearch.search(any(), anyInt(), any(), any()))
                .thenReturn(List.of(ev("C"), ev("D"), ev("E")));

        SmartSearchResult result = service.search(req("q", null, null), true);

        assertThat(result.smartResults()).extracting(EventDocument::getId).containsExactly("C", "D");
    }
}

package com.booking.platform.event_service.service.impl;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.VenueInfo;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventVectorIndexerImplTest {

    @Mock private EventRepository eventRepository;
    @Mock private VectorStore vectorStore;
    @InjectMocks private EventVectorIndexerImpl indexer;

    private static final Instant WHEN = Instant.parse("2026-01-01T20:00:00Z");

    private EventDocument sampleEvent() {
        return EventDocument.builder()
                .id("64abc")
                .title("Summer Jazz Night")
                .description("Live quartet playing smooth jazz")
                .category(EventCategory.CONCERT)
                .status(EventStatus.PUBLISHED)
                .tags(List.of("jazz", "live"))
                .venue(VenueInfo.builder().name("Blue Note").city("Berlin").build())
                .dateTime(WHEN)
                .build();
    }

    @Test
    void index_upsertsWithPrefixedIdTextAndMetadata() {
        when(eventRepository.findById("64abc")).thenReturn(Optional.of(sampleEvent()));

        indexer.index("64abc");

        // Upsert = delete previous vector for this (prefixed) id, then add the fresh one.
        verify(vectorStore).delete(List.of("evt_64abc"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document doc = captor.getValue().get(0);

        assertThat(doc.getId()).isEqualTo("evt_64abc");
        assertThat(doc.getText()).contains("Summer Jazz Night").contains("Berlin").contains("jazz");
        assertThat(doc.getMetadata())
                .containsEntry(DocumentConst.VectorStore.META_CATEGORY, "CONCERT")
                .containsEntry(DocumentConst.VectorStore.META_CITY, "Berlin")
                .containsEntry(DocumentConst.VectorStore.META_STATUS, "PUBLISHED")
                .containsEntry(DocumentConst.VectorStore.META_DATE_TIME, WHEN.toEpochMilli());
    }

    @Test
    void index_eventMissing_removesStaleVectorAndDoesNotAdd() {
        when(eventRepository.findById("gone")).thenReturn(Optional.empty());

        indexer.index("gone");

        verify(vectorStore).delete(List.of("evt_gone"));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void remove_deletesPrefixedId() {
        indexer.remove("64abc");

        verify(vectorStore).delete(List.of("evt_64abc"));
    }
}

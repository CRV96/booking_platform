package com.booking.platform.event_service.init;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventVectorIndexer;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VectorBackfillRunnerTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventVectorIndexer indexer;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private MongoCollection<Document> collection;
    @Mock private DistinctIterable<String> distinctIds;

    @InjectMocks private VectorBackfillRunner runner;

    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @BeforeEach
    void wireMongo() {
        when(mongoTemplate.getCollection(DocumentConst.VectorStore.COLLECTION_NAME)).thenReturn(collection);
        when(collection.distinct("_id", String.class)).thenReturn(distinctIds);
    }

    /** Stub the vector store's existing ids (stored as prefixed strings, e.g. "evt_1"). */
    private void alreadyIndexed(String... vectorIds) {
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(0);
            for (String id : vectorIds) {
                consumer.accept(id);
            }
            return null;
        }).when(distinctIds).forEach(any());
    }

    private EventDocument ev(String id) {
        return EventDocument.builder().id(id).build();
    }

    @Test
    void indexesOnlyMissingEvents() {
        alreadyIndexed("evt_1");
        when(eventRepository.findAll()).thenReturn(List.of(ev("1"), ev("2"), ev("3")));

        runner.run(args);

        verify(indexer, never()).index("1");
        verify(indexer).index("2");
        verify(indexer).index("3");
    }

    @Test
    void skipsWhenEverythingAlreadyIndexed() {
        alreadyIndexed("evt_1", "evt_2");
        when(eventRepository.findAll()).thenReturn(List.of(ev("1"), ev("2")));

        runner.run(args);

        verify(indexer, never()).index(anyString());
    }

    @Test
    void continuesAfterAnIndexFailure() {
        alreadyIndexed();
        when(eventRepository.findAll()).thenReturn(List.of(ev("1"), ev("2")));
        doThrow(new RuntimeException("ollama down")).when(indexer).index("1");

        runner.run(args);

        verify(indexer).index("1");
        verify(indexer).index("2"); // still attempts the rest despite the failure
    }
}

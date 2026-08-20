package com.booking.platform.event_service.init;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventVectorIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time backfill: embeds events that existed before semantic search was enabled and
 * loads them into the vector store. Ongoing changes are handled live by
 * {@code VectorIndexConsumer}; this runner only covers the pre-existing backlog.
 *
 * <p>Runs on startup when the feature is enabled, after the index has been created
 * ({@code SemanticSearchIndexConfig}), and only if the vector store is empty — so
 * restarts don't re-embed everything.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.semantic-search.enabled", havingValue = "true")
public class VectorBackfillRunner implements ApplicationRunner {

    private final EventRepository eventRepository;
    private final EventVectorIndexer indexer;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> alreadyIndexed = fetchIndexedEventIds();
        List<EventDocument> missing = eventRepository.findAll().stream()
                .filter(event -> !alreadyIndexed.contains(event.getId()))
                .toList();

        if (missing.isEmpty()) {
            log.info("Vector backfill skipped — all {} events already indexed", alreadyIndexed.size());
            return;
        }

        log.info("Vector backfill starting — {} event(s) not yet indexed ({} already present); embedding via Ollama...",
                missing.size(), alreadyIndexed.size());

        int indexed = 0;
        int failed = 0;
        for (EventDocument event : missing) {
            try {
                indexer.index(event.getId());
                indexed++;
            } catch (Exception e) {
                failed++;
                log.error("Backfill failed for event '{}': {}", event.getId(), e.getMessage());
            }
        }
        log.info("Vector backfill complete — {} indexed, {} failed, {} already present",
                indexed, failed, alreadyIndexed.size());
    }

    /**
     * Event ids already present in the vector store (its {@code _id} is the prefixed
     * event id — see {@link DocumentConst.VectorStore#ID_PREFIX}). Lets the backfill index
     * only what's missing, so it self-heals partial runs instead of skipping wholesale.
     */
    private Set<String> fetchIndexedEventIds() {
        String prefix = DocumentConst.VectorStore.ID_PREFIX;
        Set<String> eventIds = new HashSet<>();
        mongoTemplate.getCollection(DocumentConst.VectorStore.COLLECTION_NAME)
                .distinct("_id", String.class)
                .forEach(vectorId -> eventIds.add(
                        vectorId.startsWith(prefix) ? vectorId.substring(prefix.length()) : vectorId));
        return eventIds;
    }
}
